package com.cupcheck.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 订单明细。下单时快照饮品名称/容量/单价；
 * 标记“已制作”时锁定当时的自带杯折扣，之后换杯不再影响该明细。
 */
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    public Order order;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "drink_size_id")
    public DrinkSize drinkSize;

    public String drinkName;
    public String sizeName;
    public int volumeMl;
    public int unitPriceCents;

    public int quantity;

    @Enumerated(EnumType.STRING)
    public ItemStatus status = ItemStatus.PENDING;

    /** 已制作时锁定的自带杯折扣（分）；未制作时为 null，随容器状态实时计算 */
    public Integer lockedCupDiscountCents;
}
