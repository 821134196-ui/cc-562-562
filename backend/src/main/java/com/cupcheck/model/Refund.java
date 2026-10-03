package com.cupcheck.model;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 退款单。记录退款时冲回的自带杯折扣与优惠券抵扣金额，
 * 以及优惠券是否已恢复可再次使用。
 */
@Entity
@Table(name = "refund")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    public Order order;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    public Payment payment;

    public int amountCents;

    /** 冲回的自带杯折扣（分） */
    public int reversedCupDiscountCents;

    /** 冲回的优惠券抵扣（分） */
    public int reversedCouponDiscountCents;

    /** 退款时是否恢复了优惠券（ACTIVE，可再次使用） */
    public boolean couponRestored;

    public String reason;

    public Instant refundedAt = Instant.now();
}
