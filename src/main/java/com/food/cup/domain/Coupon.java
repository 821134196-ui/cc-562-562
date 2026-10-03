package com.food.cup.domain;

import jakarta.persistence.*;

/**
 * 优惠券。stackableWithPersonal=false 时不可与自带杯折扣叠加
 * （规则控制）；同一券同一时刻只能被一个有效订单使用（不能重复抵扣）。
 */
@Entity
@Table(name = "coupon")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    /** PERCENT: 百分比数值(如10)；FIXED: 立减金额(分) */
    @Column(nullable = false)
    private long value;

    /** 是否允许与自带杯折扣叠加 */
    @Column(nullable = false)
    private boolean stackableWithPersonal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status = CouponStatus.UNUSED;

    /** 当前占用该券的订单（支付后写入；退款后清空） */
    @Column
    private Long usedByOrderId;

    protected Coupon() {
    }

    public Coupon(String code, String description, CouponType type, long value, boolean stackableWithPersonal) {
        this.code = code;
        this.description = description;
        this.type = type;
        this.value = value;
        this.stackableWithPersonal = stackableWithPersonal;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public CouponType getType() { return type; }
    public long getValue() { return value; }
    public boolean isStackableWithPersonal() { return stackableWithPersonal; }
    public CouponStatus getStatus() { return status; }
    public Long getUsedByOrderId() { return usedByOrderId; }

    public void markUsed(Long orderId) {
        this.status = CouponStatus.USED;
        this.usedByOrderId = orderId;
    }

    public void revertToUnused() {
        this.status = CouponStatus.UNUSED;
        this.usedByOrderId = null;
    }
}
