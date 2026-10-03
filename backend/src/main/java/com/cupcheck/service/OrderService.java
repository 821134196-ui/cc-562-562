package com.cupcheck.service;

import java.util.List;

import com.cupcheck.model.ContainerSelection;
import com.cupcheck.model.ContainerType;
import com.cupcheck.model.Coupon;
import com.cupcheck.model.CouponStatus;
import com.cupcheck.model.CupInspection;
import com.cupcheck.model.DrinkSize;
import com.cupcheck.model.HygieneResult;
import com.cupcheck.model.ItemStatus;
import com.cupcheck.model.Order;
import com.cupcheck.model.OrderItem;
import com.cupcheck.model.OrderStatus;
import com.cupcheck.model.Payment;
import com.cupcheck.model.PaymentStatus;
import com.cupcheck.model.RejectionCode;
import com.cupcheck.model.RejectionRecord;
import com.cupcheck.model.Refund;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * 订单事务操作。每个改价操作都遵循同一模式：
 * 取订单 → 校验版本（乐观锁）→ 校验状态 → 修改 → PricingService 重算 → 提交。
 * 提交时 Hibernate 还会再校验一次 @Version，双保险处理并发改杯。
 */
@ApplicationScoped
public class OrderService {

    @Inject
    EntityManager em;

    @Inject
    PricingService pricing;

    // ---------- 查询 ----------

    public Order get(long id) {
        Order order = em.find(Order.class, id);
        if (order == null) {
            throw ApiException.notFound("订单");
        }
        return order;
    }

    public List<Order> listAll() {
        return em.createQuery("select o from Order o order by o.id desc", Order.class).getResultList();
    }

    // ---------- 创建与加购 ----------

    @Transactional
    public Order create(List<NewItem> items) {
        Order order = new Order();
        // 默认门店普通杯，不产生自带杯折扣
        order.containers.add(ContainerSelection.of(order, ContainerType.STORE_CUP, null, "system", "开单默认普通杯"));
        em.persist(order);
        if (items != null) {
            for (NewItem ni : items) {
                addItemInternal(order, ni.drinkSizeId(), ni.quantity());
            }
        }
        pricing.recalculate(order);
        em.flush();
        return order;
    }

    @Transactional
    public Order addItem(long orderId, long expectedVersion, long drinkSizeId, int quantity) {
        Order order = lockAndCheck(orderId, expectedVersion);
        requireOpen(order);
        addItemInternal(order, drinkSizeId, quantity);
        // 新增饮品可能使已生效自带杯容量不足 → 自动判拒并切回普通杯
        enforceCapacityForActiveCup(order, "system");
        pricing.recalculate(order);
        em.flush();
        return order;
    }

    private void addItemInternal(Order order, long drinkSizeId, int quantity) {
        if (quantity <= 0) {
            throw ApiException.badRequest("数量必须大于 0");
        }
        DrinkSize size = em.find(DrinkSize.class, drinkSizeId);
        if (size == null) {
            throw ApiException.notFound("饮品规格");
        }
        for (OrderItem item : order.items) {
            if (item.drinkSize.id.equals(size.id) && item.status == ItemStatus.PENDING) {
                item.quantity += quantity;
                return;
            }
        }
        OrderItem item = new OrderItem();
        item.order = order;
        item.drinkSize = size;
        item.drinkName = size.drink.name;
        item.sizeName = size.sizeName;
        item.volumeMl = size.volumeMl;
        item.unitPriceCents = size.priceCents;
        item.quantity = quantity;
        order.items.add(item);
    }

    // ---------- 容器：换杯与自带杯核验 ----------

    /** 切换为门店普通杯 */
    @Transactional
    public Order switchToStoreCup(long orderId, long expectedVersion, String changedBy, String note) {
        Order order = lockAndCheck(orderId, expectedVersion);
        requireOpen(order);
        activate(order, ContainerSelection.of(order, ContainerType.STORE_CUP, null,
                operator(changedBy), note == null || note.isBlank() ? "手动切换普通杯" : note));
        pricing.recalculate(order);
        em.flush();
        return order;
    }

    /**
     * 自带杯核验：容量 + 卫生一次判定。
     * 通过 → 自带杯生效并立即按折扣重算；
     * 拒收 → 登记拒收记录（不可删除）并自动切换普通杯，价格立即重算。
     */
    @Transactional
    public Order verifyOwnCup(long orderId, long expectedVersion, int capacityMl,
            HygieneResult hygiene, String inspector, String changedBy) {
        Order order = lockAndCheck(orderId, expectedVersion);
        requireOpen(order);
        if (capacityMl <= 0) {
            throw ApiException.badRequest("杯子容量必须大于 0");
        }
        if (hygiene == null) {
            throw ApiException.badRequest("缺少卫生检查结果");
        }

        int required = order.requiredCapacityMl();
        boolean capacityOk = capacityMl >= required;
        boolean hygieneOk = hygiene == HygieneResult.CLEAN;

        CupInspection insp = new CupInspection();
        insp.order = order;
        insp.capacityMl = capacityMl;
        insp.hygiene = hygiene;
        insp.requiredCapacityMl = required;
        insp.capacityOk = capacityOk;
        insp.accepted = capacityOk && hygieneOk;
        insp.inspector = operator(inspector);
        order.inspections.add(insp);
        em.flush(); // 核验记录先落库，拒收记录再引用它

        String who = operator(changedBy);
        if (insp.accepted) {
            activate(order, ContainerSelection.of(order, ContainerType.OWN_CUP, capacityMl, who, "自带杯核验通过"));
        } else {
            if (!hygieneOk) {
                RejectionCode code = switch (hygiene) {
                    case DIRTY_RIM -> RejectionCode.DIRTY_RIM;
                    case RESIDUE -> RejectionCode.RESIDUE;
                    case DAMAGED -> RejectionCode.DAMAGED;
                    default -> throw ApiException.badRequest("未知卫生结果");
                };
                order.rejections.add(RejectionRecord.of(order, insp, code, code.label, insp.inspector));
            }
            if (!capacityOk) {
                order.rejections.add(RejectionRecord.of(order, insp, RejectionCode.CAPACITY_INSUFFICIENT,
                        "容量不足：自带杯 " + capacityMl + "ml < 订单所需 " + required + "ml", insp.inspector));
            }
            activate(order, ContainerSelection.of(order, ContainerType.STORE_CUP, null, who, "自带杯拒收，自动切换普通杯"));
        }
        pricing.recalculate(order);
        em.flush();
        return order;
    }

    // ---------- 优惠券 ----------

    @Transactional
    public Order applyCoupon(long orderId, long expectedVersion, String code) {
        Order order = lockAndCheck(orderId, expectedVersion);
        requireOpen(order);
        Coupon coupon = em.createQuery("select c from Coupon c where c.code = :code", Coupon.class)
                .setParameter("code", code == null ? "" : code.trim().toUpperCase())
                .getResultStream().findFirst()
                .orElseThrow(() -> ApiException.notFound("优惠券"));
        if (coupon.status == CouponStatus.USED) {
            throw ApiException.conflictState("优惠券已被使用");
        }
        Long occupied = em.createQuery(
                "select count(o) from Order o where o.coupon = :c and o.status <> :refunded and o.id <> :self",
                Long.class)
                .setParameter("c", coupon)
                .setParameter("refunded", OrderStatus.REFUNDED)
                .setParameter("self", order.id)
                .getSingleResult();
        if (occupied > 0) {
            throw ApiException.conflictState("优惠券已被其他订单占用");
        }
        if (order.subtotalCents < coupon.minSpendCents) {
            throw ApiException.conflictState("未达优惠券最低消费 " + fen(coupon.minSpendCents) + " 元");
        }
        if (!coupon.stackableWithCupDiscount && order.cupDiscountCents > 0) {
            throw ApiException.conflictState("该优惠券不可与自带杯折扣叠加");
        }
        order.coupon = coupon;
        pricing.recalculate(order);
        em.flush();
        return order;
    }

    @Transactional
    public Order removeCoupon(long orderId, long expectedVersion) {
        Order order = lockAndCheck(orderId, expectedVersion);
        requireOpen(order);
        order.coupon = null;
        pricing.recalculate(order);
        em.flush();
        return order;
    }

    // ---------- 制作、支付、退款 ----------

    /** 标记饮品已制作：锁定当前自带杯折扣，此后换杯不影响该明细 */
    @Transactional
    public Order markItemMade(long orderId, long itemId, long expectedVersion) {
        Order order = lockAndCheck(orderId, expectedVersion);
        if (order.status == OrderStatus.REFUNDED) {
            throw ApiException.conflictState("订单已退款");
        }
        OrderItem item = order.items.stream()
                .filter(i -> i.id.equals(itemId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("订单明细"));
        if (item.status == ItemStatus.MADE) {
            return order; // 幂等
        }
        item.lockedCupDiscountCents = pricing.discountToLock(order, item);
        item.status = ItemStatus.MADE;
        pricing.recalculate(order);
        em.flush();
        return order;
    }

    /** 本地模拟支付：按当前价格快照收款，占用优惠券 */
    @Transactional
    public Order pay(long orderId, long expectedVersion, String method) {
        Order order = lockAndCheck(orderId, expectedVersion);
        requireOpen(order);
        if (order.items.isEmpty()) {
            throw ApiException.conflictState("订单为空，不能收款");
        }
        pricing.recalculate(order);

        Payment payment = new Payment();
        payment.order = order;
        payment.amountCents = order.totalCents;
        payment.method = (method == null || method.isBlank()) ? "MOCK_CASH" : method;
        em.persist(payment);

        if (order.coupon != null) {
            if (order.couponDiscountCents > 0) {
                order.coupon.status = CouponStatus.USED;
                order.coupon.usedByOrderId = order.id;
            } else {
                // 因叠加规则等原因未产生抵扣的券不核销、不占用
                order.coupon = null;
            }
        }
        order.status = OrderStatus.PAID;
        em.flush();
        return order;
    }

    /**
     * 本地模拟退款（整单）：支付单置为已退款，
     * 冲回自带杯折扣与优惠券抵扣，恢复优惠券为可用。
     */
    @Transactional
    public Order refund(long orderId, String reason) {
        Order order = get(orderId);
        if (order.status != OrderStatus.PAID) {
            throw ApiException.conflictState("只有已支付订单可以退款");
        }
        Payment payment = em.createQuery("select p from Payment p where p.order = :o", Payment.class)
                .setParameter("o", order).getSingleResult();
        payment.status = PaymentStatus.REFUNDED;

        Refund refund = new Refund();
        refund.order = order;
        refund.payment = payment;
        refund.amountCents = payment.amountCents;
        refund.reversedCupDiscountCents = order.cupDiscountCents;
        refund.reversedCouponDiscountCents = order.couponDiscountCents;
        refund.reason = reason == null || reason.isBlank() ? "顾客申请退款" : reason;

        if (order.coupon != null) {
            // 恢复优惠券，可再次使用
            order.coupon.status = CouponStatus.ACTIVE;
            order.coupon.usedByOrderId = null;
            refund.couponRestored = true;
        }
        em.persist(refund);
        order.status = OrderStatus.REFUNDED;
        em.flush();
        return order;
    }

    // ---------- 内部工具 ----------

    private Order lockAndCheck(long orderId, long expectedVersion) {
        Order order = get(orderId);
        if (order.version != expectedVersion) {
            throw ApiException.versionConflict();
        }
        // 推进变更计数使订单行必然脏更新：即使本次只改了容器/明细，
        // orders 行也会产生 WHERE version=? 的 UPDATE，并发写者必然有一方冲突。
        order.touchSeq++;
        return order;
    }

    private void requireOpen(Order order) {
        if (order.status != OrderStatus.OPEN) {
            throw ApiException.conflictState("订单当前状态为 " + order.status + "，不允许修改");
        }
    }

    /** 同一订单只保留一个有效容器选择：旧的全部置为 inactive，追加新记录 */
    private void activate(Order order, ContainerSelection next) {
        for (ContainerSelection c : order.containers) {
            c.active = false;
        }
        // 先落库“全部失效”，再插入新的生效记录，避免 one_active_container 唯一索引冲突
        em.flush();
        order.containers.add(next);
    }

    /** 加饮品后若生效中的自带杯容量不再达标，自动判拒并切回普通杯 */
    private void enforceCapacityForActiveCup(Order order, String operator) {
        ContainerSelection active = order.activeContainer();
        if (active == null || active.type != ContainerType.OWN_CUP) {
            return;
        }
        int required = order.requiredCapacityMl();
        if (active.capacityMl != null && active.capacityMl >= required) {
            return;
        }
        order.rejections.add(RejectionRecord.of(order, null, RejectionCode.CAPACITY_INSUFFICIENT,
                "新增饮品后容量不足：自带杯 " + active.capacityMl + "ml < 订单所需 " + required + "ml", operator));
        activate(order, ContainerSelection.of(order, ContainerType.STORE_CUP, null, operator,
                "容量不足，自动切换普通杯"));
    }

    private String operator(String name) {
        return name == null || name.isBlank() ? "cashier" : name.trim();
    }

    private String fen(int cents) {
        return String.format("%.2f", cents / 100.0);
    }

    public record NewItem(long drinkSizeId, int quantity) {
    }
}
