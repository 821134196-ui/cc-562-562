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

/** 自带杯核验记录：容量是否达标 + 卫生检查结果，一次核验一条 */
@Entity
@Table(name = "cup_inspection")
public class CupInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    public Order order;

    /** 顾客杯子标称容量（毫升） */
    public int capacityMl;

    @Enumerated(EnumType.STRING)
    public HygieneResult hygiene;

    /** 核验时订单所需的最低容量（毫升） */
    public int requiredCapacityMl;

    public boolean capacityOk;

    /** 容量与卫生均通过才为 true */
    public boolean accepted;

    public String inspector;

    public Instant inspectedAt = Instant.now();
}
