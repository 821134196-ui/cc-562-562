package com.cupcheck.service;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.cupcheck.model.ContainerSelection;
import com.cupcheck.model.ContainerType;
import com.cupcheck.model.Coupon;
import com.cupcheck.model.CouponType;
import com.cupcheck.model.ItemStatus;
import com.cupcheck.model.Order;
import com.cupcheck.model.OrderItem;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * 计价规则集中在这里。所有金额单位为分，避免浮点误差。
 *
 * 规则：
 * 1. 自带杯通过核验后，每件“尚未制作”的饮品减免 app.cup-discount-cents；
 *    已制作的饮品使用制作时锁定的折扣，换杯不再影响。
 * 2. 优惠券按券面规则抵扣；stackableWithCupDiscount=false 时
 *    与自带杯折扣互斥（券失效、抵扣为 0），且总折扣不超过小计（不重复抵扣）。
 */
@ApplicationScoped
public class PricingService {

    @ConfigProperty(name = "app.cup-discount-cents")
    int cupDiscountPerItem;

    public int cupDiscountPerItem() {
        return cupDiscountPerItem;
    }

    /** 重算订单价格快照并写回实体（调用方负责事务） */
    public void recalculate(Order order) {
        int subtotal = 0;
        for (OrderItem item : order.items) {
            subtotal += item.unitPriceCents * item.quantity;
        }

        boolean cupValid = cupCurrentlyValid(order);
        int cupDiscount = 0;
        for (OrderItem item : order.items) {
            cupDiscount += itemCupDiscount(item, cupValid);
        }

        int couponDiscount = couponDiscount(order.coupon, subtotal, cupDiscount);

        order.subtotalCents = subtotal;
        order.cupDiscountCents = cupDiscount;
        order.couponDiscountCents = couponDiscount;
        order.totalCents = Math.max(0, subtotal - cupDiscount - couponDiscount);
    }

    /** 当前生效容器是否为可用的自带杯（只有通过核验才会出现 OWN_CUP 生效记录） */
    public boolean cupCurrentlyValid(Order order) {
        ContainerSelection active = order.activeContainer();
        return active != null && active.type == ContainerType.OWN_CUP;
    }

    /** 单个明细当前应计的自带杯折扣；已制作明细返回锁定值 */
    public int itemCupDiscount(OrderItem item, boolean cupValid) {
        if (item.status == ItemStatus.MADE) {
            return item.lockedCupDiscountCents == null ? 0 : item.lockedCupDiscountCents;
        }
        return cupValid ? cupDiscountPerItem * item.quantity : 0;
    }

    /** 标记制作时调用：按当前容器状态计算并返回应锁定的折扣 */
    public int discountToLock(Order order, OrderItem item) {
        return cupCurrentlyValid(order) ? cupDiscountPerItem * item.quantity : 0;
    }

    /** 优惠券抵扣额；不满足规则时返回 0（不重复抵扣、不超过小计） */
    public int couponDiscount(Coupon coupon, int subtotalCents, int cupDiscountCents) {
        if (coupon == null) {
            return 0;
        }
        if (subtotalCents < coupon.minSpendCents) {
            return 0;
        }
        if (!coupon.stackableWithCupDiscount && cupDiscountCents > 0) {
            return 0;
        }
        int raw = coupon.type == CouponType.FIXED_AMOUNT
                ? coupon.value
                : subtotalCents * coupon.value / 100;
        // 总折扣不得超过小计
        return Math.min(raw, Math.max(0, subtotalCents - cupDiscountCents));
    }
}
