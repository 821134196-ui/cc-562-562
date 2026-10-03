package com.cupcheck.api.dto;

import java.time.Instant;
import java.util.List;

import com.cupcheck.model.ContainerSelection;
import com.cupcheck.model.Coupon;
import com.cupcheck.model.CupInspection;
import com.cupcheck.model.Order;
import com.cupcheck.model.OrderItem;
import com.cupcheck.model.Payment;
import com.cupcheck.model.RejectionRecord;
import com.cupcheck.model.Refund;

/** 订单详情视图：点单页一次拿全规格、容器、核验、价格、支付/退款信息 */
public record OrderDetailDto(
        long id,
        long version,
        String status,
        List<ItemDto> items,
        ContainerDto container,
        List<ContainerDto> containerHistory,
        CouponDto coupon,
        PricingDto pricing,
        int requiredCapacityMl,
        int cupDiscountPerItemCents,
        List<InspectionDto> inspections,
        List<RejectionDto> rejections,
        PaymentDto payment,
        List<RefundDto> refunds,
        Instant createdAt) {

    public record ItemDto(long id, String drinkName, String sizeName, int volumeMl,
            int unitPriceCents, int quantity, String status, int cupDiscountCents) {
        static ItemDto of(OrderItem i, int cupDiscountCents) {
            return new ItemDto(i.id, i.drinkName, i.sizeName, i.volumeMl,
                    i.unitPriceCents, i.quantity, i.status.name(), cupDiscountCents);
        }
    }

    public record ContainerDto(long id, String type, Integer capacityMl, boolean active,
            String changedBy, String note, Instant selectedAt) {
        static ContainerDto of(ContainerSelection c) {
            return new ContainerDto(c.id, c.type.name(), c.capacityMl, c.active,
                    c.changedBy, c.note, c.selectedAt);
        }
    }

    public record CouponDto(String code, String type, int value, int minSpendCents,
            boolean stackableWithCupDiscount, String status) {
        static CouponDto of(Coupon c) {
            return new CouponDto(c.code, c.type.name(), c.value, c.minSpendCents,
                    c.stackableWithCupDiscount, c.status.name());
        }
    }

    public record PricingDto(int subtotalCents, int cupDiscountCents,
            int couponDiscountCents, int totalCents) {
    }

    public record InspectionDto(long id, int capacityMl, String hygiene, int requiredCapacityMl,
            boolean capacityOk, boolean accepted, String inspector, Instant inspectedAt) {
        static InspectionDto of(CupInspection i) {
            return new InspectionDto(i.id, i.capacityMl, i.hygiene.name(), i.requiredCapacityMl,
                    i.capacityOk, i.accepted, i.inspector, i.inspectedAt);
        }
    }

    public record RejectionDto(long id, String code, String reason, String operator, Instant createdAt) {
        static RejectionDto of(RejectionRecord r) {
            return new RejectionDto(r.id, r.code.name(), r.reason, r.operator, r.createdAt);
        }
    }

    public record PaymentDto(long id, int amountCents, String status, String method, Instant paidAt) {
        static PaymentDto of(Payment p) {
            return new PaymentDto(p.id, p.amountCents, p.status.name(), p.method, p.paidAt);
        }
    }

    public record RefundDto(long id, int amountCents, int reversedCupDiscountCents,
            int reversedCouponDiscountCents, boolean couponRestored, String reason, Instant refundedAt) {
        static RefundDto of(Refund r) {
            return new RefundDto(r.id, r.amountCents, r.reversedCupDiscountCents,
                    r.reversedCouponDiscountCents, r.couponRestored, r.reason, r.refundedAt);
        }
    }

    public record SummaryDto(long id, long version, String status, int itemCount,
            int totalCents, String containerType, Instant createdAt) {
        public static SummaryDto of(Order o) {
            ContainerSelection active = o.activeContainer();
            return new SummaryDto(o.id, o.version, o.status.name(), o.items.size(),
                    o.totalCents, active == null ? "NONE" : active.type.name(), o.createdAt);
        }
    }
}
