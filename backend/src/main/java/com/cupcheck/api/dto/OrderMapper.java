package com.cupcheck.api.dto;

import java.util.Comparator;
import java.util.List;

import com.cupcheck.model.Order;
import com.cupcheck.model.Payment;
import com.cupcheck.model.Refund;
import com.cupcheck.service.PricingService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** 实体 → DTO。必须在事务内调用（惰性集合需要打开的 session）。 */
@ApplicationScoped
public class OrderMapper {

    @Inject
    EntityManager em;

    @Inject
    PricingService pricing;

    public OrderDetailDto toDetail(Order o) {
        boolean cupValid = pricing.cupCurrentlyValid(o);
        List<OrderDetailDto.ItemDto> items = o.items.stream()
                .map(i -> OrderDetailDto.ItemDto.of(i, pricing.itemCupDiscount(i, cupValid)))
                .toList();

        List<OrderDetailDto.ContainerDto> history = o.containers.stream()
                .sorted(Comparator.comparing(c -> c.selectedAt))
                .map(OrderDetailDto.ContainerDto::of)
                .toList();
        OrderDetailDto.ContainerDto active = o.containers.stream()
                .filter(c -> c.active).findFirst()
                .map(OrderDetailDto.ContainerDto::of).orElse(null);

        Payment payment = em.createQuery("select p from Payment p where p.order = :o", Payment.class)
                .setParameter("o", o).getResultStream().findFirst().orElse(null);
        List<Refund> refunds = em.createQuery("select r from Refund r where r.order = :o order by r.id", Refund.class)
                .setParameter("o", o).getResultList();

        return new OrderDetailDto(
                o.id,
                o.version,
                o.status.name(),
                items,
                active,
                history,
                o.coupon == null ? null : OrderDetailDto.CouponDto.of(o.coupon),
                new OrderDetailDto.PricingDto(o.subtotalCents, o.cupDiscountCents,
                        o.couponDiscountCents, o.totalCents),
                o.requiredCapacityMl(),
                pricing.cupDiscountPerItem(),
                o.inspections.stream().map(OrderDetailDto.InspectionDto::of).toList(),
                o.rejections.stream().map(OrderDetailDto.RejectionDto::of).toList(),
                payment == null ? null : OrderDetailDto.PaymentDto.of(payment),
                refunds.stream().map(OrderDetailDto.RefundDto::of).toList(),
                o.createdAt);
    }

    public List<OrderDetailDto.SummaryDto> toSummaries(List<Order> orders) {
        return orders.stream().map(OrderDetailDto.SummaryDto::of).toList();
    }
}
