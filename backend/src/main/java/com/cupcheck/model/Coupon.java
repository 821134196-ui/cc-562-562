package com.cupcheck.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 优惠券。stackableWithCupDiscount 是叠加规则开关：
 * false 时若订单已享自带杯折扣则不可用，避免重复抵扣。
 */
@Entity
@Table(name = "coupon")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(unique = true, nullable = false)
    public String code;

    @Enumerated(EnumType.STRING)
    public CouponType type;

    /** FIXED_AMOUNT: 减免金额（分）；PERCENT: 折扣百分比（如 10 = 九折减 10%） */
    public int value;

    public int minSpendCents;

    public boolean stackableWithCupDiscount;

    @Enumerated(EnumType.STRING)
    public CouponStatus status = CouponStatus.ACTIVE;

    /** 支付成功后占用该券的订单；退款后清空并恢复为 ACTIVE */
    public Long usedByOrderId;
}
