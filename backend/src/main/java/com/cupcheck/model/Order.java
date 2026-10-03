package com.cupcheck.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 订单聚合根。所有会改价的操作都在事务内重算价格快照，
 * version 字段用于乐观锁：两名店员同时改杯时后提交者收到 409。
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public long version;

    /** 变更计数：任何改价操作都 +1，保证订单行产生带版本条件的 UPDATE（乐观锁生效） */
    public long touchSeq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public OrderStatus status = OrderStatus.OPEN;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id")
    public List<OrderItem> items = new ArrayList<>();

    /** 容器选择历史，最多一条 active=true（由部分唯一索引保证） */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("id")
    public List<ContainerSelection> containers = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("id")
    public List<CupInspection> inspections = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("id")
    public List<RejectionRecord> rejections = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    public Coupon coupon;

    // ---- 价格快照：每次变更后由 PricingService 重算并落库 ----
    public int subtotalCents;
    public int cupDiscountCents;
    public int couponDiscountCents;
    public int totalCents;

    public Instant createdAt = Instant.now();

    public ContainerSelection activeContainer() {
        for (ContainerSelection c : containers) {
            if (c.active) {
                return c;
            }
        }
        return null;
    }

    /** 订单中最大单杯容量，即自带杯需要满足的最低容量 */
    public int requiredCapacityMl() {
        int max = 0;
        for (OrderItem item : items) {
            max = Math.max(max, item.volumeMl);
        }
        return max;
    }
}
