package com.food.cup.web.dto;

import com.food.cup.domain.*;
import com.food.cup.service.PricingService;

import java.time.Instant;
import java.util.List;

/**
 * 点单页完整视图：饮品规格、杯具容量、卫生检查结果、价格明细、
 * 拒收记录（不可删）、变化时间线、乐观锁版本一次给到。
 */
public record OrderView(
        Long id,
        long version,
        String status,
        BeverageInfo beverage,
        int quantity,
        String containerType,
        Integer personalCupMl,
        String checkStatus,
        int requiredMl,
        boolean personalDiscountActive,
        int personalDiscountPercent,
        CouponInfo coupon,
        boolean couponStackable,
        PriceInfo price,
        String paymentRef,
        Instant paidAt,
        String refundRef,
        Instant refundedAt,
        List<RejectionInfo> rejections,
        List<EventInfo> events
) {
    public record BeverageInfo(Long id, String name, String sizeName, int sizeMl, long basePriceCents) {}
    public record CouponInfo(Long id, String code, String description, String type, long value,
                             boolean stackableWithPersonal, String status) {}
    public record PriceInfo(long baseTotalCents, long personalDiscountCents,
                            long couponDiscountCents, long finalPriceCents) {}
    public record RejectionInfo(Long id, Instant createdAt, String reason, int cupMl, int requiredMl, String note) {}
    public record EventInfo(Long id, Instant createdAt, String action, String detail) {}

    public static OrderView of(PosOrder o, PricingService pricing) {
        Beverage b = o.getBeverage();
        Coupon c = o.getCoupon();
        return new OrderView(
                o.getId(),
                o.getVersion(),
                o.getStatus().name(),
                new BeverageInfo(b.getId(), b.getName(), b.getSizeName(), b.getSizeMl(), b.getBasePriceCents()),
                o.getQuantity(),
                o.getContainerType().name(),
                o.getPersonalCupMl(),
                o.getCheckStatus().name(),
                b.getSizeMl(),
                pricing.personalDiscountApplies(o),
                pricing.getPersonalDiscountPercent(),
                c == null ? null : new CouponInfo(c.getId(), c.getCode(), c.getDescription(),
                        c.getType().name(), c.getValue(), c.isStackableWithPersonal(), c.getStatus().name()),
                c == null || c.isStackableWithPersonal(),
                new PriceInfo(o.getBaseTotalCents(), o.getPersonalDiscountCents(),
                        o.getCouponDiscountCents(), o.getFinalPriceCents()),
                o.getPaymentRef(), o.getPaidAt(), o.getRefundRef(), o.getRefundedAt(),
                o.getRejections().stream()
                        .map(r -> new RejectionInfo(r.getId(), r.getCreatedAt(), r.getReason().name(),
                                r.getCupMl(), r.getRequiredMl(), r.getNote()))
                        .toList(),
                o.getEvents().stream()
                        .map(e -> new EventInfo(e.getId(), e.getCreatedAt(), e.getAction().name(), e.getDetail()))
                        .toList()
        );
    }
}
