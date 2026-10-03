package com.food.cup.service;

import com.food.cup.domain.*;
import com.food.cup.repo.BeverageRepository;
import com.food.cup.repo.CouponRepository;
import com.food.cup.repo.OrderRepository;
import com.food.cup.web.dto.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * 收银业务规则（每个方法一个事务）：
 *
 * <ul>
 *   <li><b>未制作(CREATED)</b>：可核验/拒收、自由换杯、增减券，每次操作立即整体重算价格。</li>
 *   <li><b>已制作(PREPARED/PAID)</b>：容器与价格锁定，换杯被拒绝并记录 BLOCKED 事件。</li>
 *   <li><b>并发</b>：请求必须携带客户端看到的 version；与服务端不一致立即 409，
 *       JPA 乐观锁(@Version)再兜底，保证两人同时改杯后价格与最终选择一致。</li>
 *   <li><b>券</b>：应用即占用(USED)，不能重复抵扣；换合格自带杯而券不可叠加时自动摘下并恢复；
 *       退款时冲回恢复为未使用。</li>
 * </ul>
 */
@ApplicationScoped
public class OrderService {

    private final OrderRepository orders;
    private final BeverageRepository beverages;
    private final CouponRepository coupons;
    private final PricingService pricing;
    private final AuditService audit;

    public OrderService(OrderRepository orders, BeverageRepository beverages,
                        CouponRepository coupons, PricingService pricing, AuditService audit) {
        this.orders = orders;
        this.beverages = beverages;
        this.coupons = coupons;
        this.pricing = pricing;
        this.audit = audit;
    }

    @Transactional
    public OrderView create(CreateOrderRequest req) {
        Beverage beverage = beverages.find(req.beverageId())
                .orElseThrow(() -> new BusinessException("BEVERAGE_NOT_FOUND", "饮品不存在: " + req.beverageId()));
        if (req.quantity() <= 0) {
            throw new BusinessException("BAD_QUANTITY", "数量必须大于 0");
        }
        PosOrder order = new PosOrder(beverage, req.quantity());

        boolean wantsPersonal = req.personalCupMl() != null;
        if (wantsPersonal) {
            // 下单即带杯：先登记，核验结论由 /inspect 给出（未核验前不享折扣）
            order.usePersonalCup(req.personalCupMl());
        }
        pricing.recalculate(order);
        order.addEvent(EventAction.CREATED,
                (wantsPersonal ? "顾客下单时提供自带杯(" + req.personalCupMl() + "ml)，待核验"
                               : "普通杯下单")
                        + "，数量 " + req.quantity());
        orders.persist(order);
        return view(order);
    }

    /**
     * 自带杯核验：杯口不洁或容量不足 → 登记拒收（不可删除）并切换普通杯，立即重算；
     * 合格 → 核验通过、享受自带杯折扣；此时若已挂不可叠加券，自动摘下并冲回。
     */
    @Transactional
    public OrderView inspect(Long id, InspectRequest req) {
        PosOrder order = requireMutable(id, req.version());
        order.usePersonalCup(req.cupMl());

        int requiredMl = order.getBeverage().getSizeMl();
        boolean capacityOk = req.cupMl() >= requiredMl;
        boolean rimOk = req.rimClean();

        if (!capacityOk) {
            HygieneRejection rejection = new HygieneRejection(order,
                    RejectReason.INSUFFICIENT_CAPACITY, req.cupMl(), requiredMl, req.note());
            order.addRejection(rejection);
            order.fallBackToDisposable(CheckStatus.REJECTED);
            order.addEvent(EventAction.CAPACITY_REJECTED,
                    "自带杯 " + req.cupMl() + "ml < 规格要求 " + requiredMl
                            + "ml，登记拒收并切换普通杯");
        } else if (!rimOk) {
            HygieneRejection rejection = new HygieneRejection(order,
                    RejectReason.RIM_UNSANITARY, req.cupMl(), requiredMl, req.note());
            order.addRejection(rejection);
            order.fallBackToDisposable(CheckStatus.REJECTED);
            order.addEvent(EventAction.RIM_REJECTED,
                    "杯口不洁，登记拒收并切换普通杯");
        } else {
            order.passCheck();
            order.addEvent(EventAction.CHECK_PASSED,
                    "自带杯 " + req.cupMl() + "ml 核验通过（容量≥" + requiredMl + "ml，杯口洁净）");
            autoRemoveNonStackableCoupon(order);
        }

        pricing.recalculate(order);
        return view(order);
    }

    /** 顾客中途换杯。未制作可换并重新计价；已制作拒绝并留痕。 */
    @Transactional
    public OrderView switchCup(Long id, SwitchCupRequest req) {
        PosOrder order = orders.findById(id);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在: " + id);
        }
        if (order.getStatus() != OrderStatus.CREATED) {
            // 不同规则：饮品已制作，容器锁定。拒绝事件用独立事务落库（本事务随后回滚）。
            audit.recordEvent(order.getId(), EventAction.BLOCKED_AFTER_PREPARED,
                    "订单状态 " + order.getStatus() + "，饮品已制作/完结，换杯被拒绝（容器保持 "
                            + order.getContainerType() + "）");
            throw new BusinessException("ORDER_LOCKED",
                    "订单当前状态 " + order.getStatus() + "，饮品已制作，不能再更换容器");
        }
        checkVersion(order, req.version());

        if (req.target() == ContainerType.DISPOSABLE) {
            order.switchToDisposable();
            order.addEvent(EventAction.SWITCH_TO_DISPOSABLE, "顾客改用门店普通杯");
            pricing.recalculate(order);
            return view(order);
        }

        // 换成自带杯：必须当场核验
        if (req.personalCupMl() == null) {
            throw new BusinessException("CUP_ML_REQUIRED", "换用自带杯必须提供杯子容量(ml)");
        }
        int cupMl = req.personalCupMl();
        int requiredMl = order.getBeverage().getSizeMl();
        boolean rimClean = req.rimClean() == null || req.rimClean();
        order.usePersonalCup(cupMl);

        if (cupMl < requiredMl) {
            order.addRejection(new HygieneRejection(order,
                    RejectReason.INSUFFICIENT_CAPACITY, cupMl, requiredMl, req.note()));
            order.fallBackToDisposable(CheckStatus.REJECTED);
            order.addEvent(EventAction.CAPACITY_REJECTED,
                    "换杯核验：自带杯 " + cupMl + "ml < 要求 " + requiredMl + "ml，拒收并保持普通杯");
        } else if (!rimClean) {
            order.addRejection(new HygieneRejection(order,
                    RejectReason.RIM_UNSANITARY, cupMl, requiredMl, req.note()));
            order.fallBackToDisposable(CheckStatus.REJECTED);
            order.addEvent(EventAction.RIM_REJECTED,
                    "换杯核验：杯口不洁，拒收并保持普通杯");
        } else {
            order.passCheck();
            order.addEvent(EventAction.CHECK_PASSED,
                    "顾客换成自带杯 " + cupMl + "ml，核验通过");
            autoRemoveNonStackableCoupon(order);
        }
        pricing.recalculate(order);
        return view(order);
    }

    @Transactional
    public OrderView applyCoupon(Long id, CouponRequest req) {
        PosOrder order = requireMutable(id, req.version());
        Coupon coupon = coupons.findByCode(req.code())
                .orElseThrow(() -> new BusinessException("COUPON_NOT_FOUND", "优惠券不存在: " + req.code()));

        if (order.getCoupon() != null && order.getCoupon().getId().equals(coupon.getId())) {
            return view(order); // 幂等
        }
        if (coupon.getStatus() == CouponStatus.USED
                && !coupon.getUsedByOrderId().equals(order.getId())) {
            throw new BusinessException("COUPON_IN_USE",
                    "优惠券 " + coupon.getCode() + " 已被订单 " + coupon.getUsedByOrderId() + " 使用，不能重复抵扣");
        }
        // 规则控制：不可叠加的券不能挂在“合格自带杯”订单上
        if (!coupon.isStackableWithPersonal() && pricing.personalDiscountApplies(order)) {
            throw new BusinessException("COUPON_NOT_STACKABLE",
                    "优惠券 " + coupon.getCode() + " 不可与自带杯折扣叠加");
        }
        coupon.markUsed(order.getId()); // 应用即占用
        order.setCoupon(coupon);
        order.addEvent(EventAction.COUPON_APPLIED, "应用优惠券 " + coupon.getCode() + "（" + coupon.getDescription() + "）");
        pricing.recalculate(order);
        return view(order);
    }

    @Transactional
    public OrderView removeCoupon(Long id, VersionRequest req) {
        PosOrder order = requireMutable(id, req.version());
        Coupon coupon = order.getCoupon();
        if (coupon != null) {
            coupon.revertToUnused();
            order.setCoupon(null);
            order.addEvent(EventAction.COUPON_REMOVED, "移除优惠券 " + coupon.getCode());
            pricing.recalculate(order);
        }
        return view(order);
    }

    @Transactional
    public OrderView markPrepared(Long id, VersionRequest req) {
        PosOrder order = requireMutable(id, req.version());
        order.markPrepared();
        order.addEvent(EventAction.MARK_PREPARED, "饮品制作完成，容器与价格锁定");
        return view(order);
    }

    /** 本地模拟收银支付。 */
    @Transactional
    public OrderView pay(Long id, VersionRequest req) {
        PosOrder order = orders.findById(id);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在: " + id);
        }
        if (order.getStatus() != OrderStatus.CREATED && order.getStatus() != OrderStatus.PREPARED) {
            throw new BusinessException("ORDER_NOT_PAYABLE", "订单状态 " + order.getStatus() + "，不可支付");
        }
        checkVersion(order, req.version());
        String ref = "PAY-" + order.getId() + "-" + Long.toHexString(System.nanoTime());
        order.markPaid(ref);
        order.addEvent(EventAction.PAID, "本地模拟支付成功 " + ref + "，支付金额 " + order.getFinalPriceCents() + " 分");
        return view(order);
    }

    /** 本地模拟退款：冲回已占用优惠券，使其可再次使用；价格快照保留备查。 */
    @Transactional
    public OrderView refund(Long id, VersionRequest req) {
        PosOrder order = orders.findById(id);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在: " + id);
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException("ORDER_NOT_REFUNDABLE", "只有已支付订单可以退款，当前状态 " + order.getStatus());
        }
        checkVersion(order, req.version());

        String ref = "RFD-" + order.getId() + "-" + Long.toHexString(System.nanoTime());
        order.markRefunded(ref);
        order.addEvent(EventAction.REFUNDED, "本地模拟退款成功 " + ref
                + "，原路退回 " + order.getFinalPriceCents() + " 分");
        if (order.getCoupon() != null) {
            String code = order.getCoupon().getCode();
            order.getCoupon().revertToUnused(); // 优惠冲回：券恢复未使用
            order.addEvent(EventAction.COUPON_REVERTED_BY_REFUND,
                    "退款冲回：优惠券 " + code + " 已恢复为未使用，可再次使用");
        }
        return view(order);
    }

    @Transactional
    public OrderView get(Long id) {
        PosOrder order = orders.findById(id);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在: " + id);
        }
        return view(order);
    }


    /** 返回视图前强制 flush：让 @Version 递增、待生成 id 落地，客户端拿到的版本即最新。 */
    private OrderView view(PosOrder order) {
        orders.flush();
        return OrderView.of(order, pricing);
    }

    private PosOrder requireMutable(Long id, Long clientVersion) {
        PosOrder order = orders.findById(id);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在: " + id);
        }
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessException("ORDER_LOCKED",
                    "订单当前状态 " + order.getStatus() + "，容器与价格已锁定");
        }
        checkVersion(order, clientVersion);
        return order;
    }

    /** 第一时间拦截过期客户端：两人同时改杯时，基于旧版本的第二人立即得到 409。 */
    private void checkVersion(PosOrder order, Long clientVersion) {
        if (clientVersion != null && clientVersion != order.getVersion()) {
            throw new VersionConflictException(order.getVersion());
        }
    }

    private void autoRemoveNonStackableCoupon(PosOrder order) {
        Coupon coupon = order.getCoupon();
        if (coupon != null && !coupon.isStackableWithPersonal()) {
            coupon.revertToUnused();
            order.setCoupon(null);
            order.addEvent(EventAction.COUPON_AUTO_REMOVED_BY_RULE,
                    "自带杯核验通过，优惠券 " + coupon.getCode() + " 按规则不可叠加，已自动摘下并恢复未使用");
        }
    }
}
