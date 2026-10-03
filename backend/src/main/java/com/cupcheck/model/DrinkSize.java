package com.cupcheck.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 可点单的饮品规格（含容量与价格，容量用于自带杯核验） */
@Entity
@Table(name = "drink_size")
public class DrinkSize {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @JsonIgnore
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "drink_id")
    public Drink drink;

    /** 中杯 / 大杯 */
    public String sizeName;

    /** 出品容量（毫升），自带杯容量不得低于订单中最大值 */
    public int volumeMl;

    /** 单价（分） */
    public int priceCents;
}
