package com.food.cup.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 收银订单。
 *
 * <p>容器选择规则：一个订单同一时刻只有一个有效容器（{@link #containerType}）。
 * 卫生拒收时自动切换为普通杯；顾客中途换杯会留下 {@link OrderEvent} 时间线。
 *
 * <p>并发：{@link #version} 为 JPA 乐观锁版本号，两名店员同时改杯时，
 * 后提交的一方收到 409，必须刷新后基于最新价格/容器状态重试。
 */
@Entity
@Table(name = "pos_order")
public class PosOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Beverage beverage;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.CREATED;

    /** 唯一有效容器选择 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContainerType containerType = ContainerType.DISPOSABLE;

    /** 当前自带杯标称容量（毫升）；普通杯时为 null */
    @Column
    private Integer personalCupMl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckStatus checkStatus = CheckStatus.NOT_REQUIRED;

    @ManyToOne(fetch = FetchType.LAZY)
    private Coupon coupon;

    /* ------- 计价快照（每次变化由 PricingService 重算，单位：分） ------- */
    @Column(nullable = false)
    private long baseTotalCents;
    @Column(nullable = false)
    private long personalDiscountCents;
    @Column(nullable = false)
    private long couponDiscountCents;
    @Column(nullable = false)
    private long finalPriceCents;

    /* ------- 本地模拟支付 ------- */
    @Column
    private String paymentRef;
    @Column
    private Instant paidAt;
    @Column
    private Instant refundedAt;
    @Column
    private String refundRef;

    @Version
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("id ASC")
    private List<HygieneRejection> rejections = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("id ASC")
    private List<OrderEvent> events = new ArrayList<>();

    protected PosOrder() {
    }

    public PosOrder(Beverage beverage, int quantity) {
        this.beverage = beverage;
        this.quantity = quantity;
    }

    public void addEvent(EventAction action, String detail) {
        events.add(new OrderEvent(this, action, detail));
    }

    public void addRejection(HygieneRejection rejection) {
        rejections.add(rejection);
    }

    /* ------- getters ------- */
    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Beverage getBeverage() { return beverage; }
    public int getQuantity() { return quantity; }
    public OrderStatus getStatus() { return status; }
    public ContainerType getContainerType() { return containerType; }
    public Integer getPersonalCupMl() { return personalCupMl; }
    public CheckStatus getCheckStatus() { return checkStatus; }
    public Coupon getCoupon() { return coupon; }
    public long getBaseTotalCents() { return baseTotalCents; }
    public long getPersonalDiscountCents() { return personalDiscountCents; }
    public long getCouponDiscountCents() { return couponDiscountCents; }
    public long getFinalPriceCents() { return finalPriceCents; }
    public String getPaymentRef() { return paymentRef; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getRefundedAt() { return refundedAt; }
    public String getRefundRef() { return refundRef; }
    public long getVersion() { return version; }
    public List<HygieneRejection> getRejections() { return rejections; }
    public List<OrderEvent> getEvents() { return events; }

    /* ------- state transitions ------- */
    public void usePersonalCup(int cupMl) {
        this.containerType = ContainerType.PERSONAL;
        this.personalCupMl = cupMl;
        this.checkStatus = CheckStatus.PENDING;
    }

    public void passCheck() {
        this.checkStatus = CheckStatus.PASSED;
    }

    /** 卫生/容量拒收：记录留在 rejections（不可删），容器唯一选择切回普通杯。 */
    public void fallBackToDisposable(CheckStatus checkStatus) {
        this.containerType = ContainerType.DISPOSABLE;
        this.personalCupMl = null;
        this.checkStatus = checkStatus;
    }

    public void switchToDisposable() {
        this.containerType = ContainerType.DISPOSABLE;
        this.personalCupMl = null;
        this.checkStatus = CheckStatus.NOT_REQUIRED;
    }

    public void setCoupon(Coupon coupon) { this.coupon = coupon; }

    public void applyPricing(long base, long personal, long couponDisc, long finalPrice) {
        this.baseTotalCents = base;
        this.personalDiscountCents = personal;
        this.couponDiscountCents = couponDisc;
        this.finalPriceCents = finalPrice;
    }

    public void markPrepared() { this.status = OrderStatus.PREPARED; }

    public void markPaid(String paymentRef) {
        this.status = OrderStatus.PAID;
        this.paymentRef = paymentRef;
        this.paidAt = Instant.now();
    }

    public void markRefunded(String refundRef) {
        this.status = OrderStatus.REFUNDED;
        this.refundRef = refundRef;
        this.refundedAt = Instant.now();
    }
}
