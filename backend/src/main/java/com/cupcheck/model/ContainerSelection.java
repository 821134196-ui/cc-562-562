package com.cupcheck.model;

import java.time.Instant;

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
 * 一次容器选择。同一订单任意时刻只有一条 active=true，
 * 换杯 = 旧记录置为 inactive + 追加新记录，历史全程留痕。
 */
@Entity
@Table(name = "container_selection")
public class ContainerSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    public Order order;

    @Enumerated(EnumType.STRING)
    public ContainerType type;

    /** 自带杯标称容量（毫升）；普通杯为 null */
    public Integer capacityMl;

    public boolean active;

    public String changedBy;

    /** 变更原因，例如“自带杯拒收，自动切换普通杯” */
    public String note;

    public Instant selectedAt = Instant.now();

    public static ContainerSelection of(Order order, ContainerType type, Integer capacityMl,
            String changedBy, String note) {
        ContainerSelection c = new ContainerSelection();
        c.order = order;
        c.type = type;
        c.capacityMl = capacityMl;
        c.active = true;
        c.changedBy = changedBy;
        c.note = note;
        return c;
    }
}
