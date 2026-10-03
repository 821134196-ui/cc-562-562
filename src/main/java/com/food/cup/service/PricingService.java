package com.food.cup.service;

import com.food.cup.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * 计价规则（纯函数式重算，任何容器/券变化都整体重算，保证价格与状态一致）：
 *
 * <ol>
 *   <li>基础金额 = 单价 × 数量。</li>
 *   <li>自带杯折扣：仅当容器=自带杯且卫生核验通过时生效。</li>
 *   <li>优惠券折扣：在「自带杯折后价」上计算，天然不会与自带杯折扣重复抵扣同一笔金额；
 *       能否与自带杯折扣同时存在由 {@link Coupon#isStackableWithPersonal()} 规则控制
 *       （应用/换杯时强制）。</li>
 *   <li>所有折扣下限为 0，最终价格不会为负。金额单位：分。</li>
 * </ol>
 */
@ApplicationScoped
public class PricingService {

    private final int personalDiscountPercent;

    public PricingService(@ConfigProperty(name = "pos.pricing.personal-discount-percent",
                                          defaultValue = "5") int personalDiscountPercent) {
        this.personalDiscountPercent = personalDiscountPercent;
    }

    public int getPersonalDiscountPercent() {
        return personalDiscountPercent;
    }

    public boolean personalDiscountApplies(PosOrder order) {
        return order.getContainerType() == ContainerType.PERSONAL
                && order.getCheckStatus() == CheckStatus.PASSED;
    }

    /** 按订单当前状态重算并写回价格快照。 */
    public void recalculate(PosOrder order) {
        long base = order.getBeverage().getBasePriceCents() * order.getQuantity();

        long personalDiscount = 0;
        long subtotal = base;
        if (personalDiscountApplies(order)) {
            personalDiscount = percentOf(base, personalDiscountPercent);
            subtotal = base - personalDiscount;
        }

        long couponDiscount = 0;
        Coupon coupon = order.getCoupon();
        if (coupon != null) {
            couponDiscount = switch (coupon.getType()) {
                case PERCENT -> percentOf(subtotal, (int) coupon.getValue());
                case FIXED -> Math.min(coupon.getValue(), subtotal);
            };
        }

        long finalPrice = Math.max(0, subtotal - couponDiscount);
        order.applyPricing(base, personalDiscount, couponDiscount, finalPrice);
    }

    private static long percentOf(long amountCents, int percent) {
        if (percent <= 0) return 0;
        if (percent >= 100) return amountCents;
        // 四舍五入到分
        return (amountCents * percent + 50) / 100;
    }
}
