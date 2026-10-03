package com.food.cup.domain;

import jakarta.persistence.*;

/**
 * 饮品规格（SKU）。sizeMl 为该规格需要的杯具容量，
 * 自带杯容量小于该值即判定容量不足。
 * 金额单位统一为「分」，避免浮点误差。
 */
@Entity
@Table(name = "beverage")
public class Beverage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** 规格描述，如 中杯/大杯 */
    @Column(nullable = false)
    private String sizeName;

    /** 所需容量（毫升） */
    @Column(nullable = false)
    private int sizeMl;

    /** 基础售价（分） */
    @Column(nullable = false)
    private long basePriceCents;

    protected Beverage() {
    }

    public Beverage(String name, String sizeName, int sizeMl, long basePriceCents) {
        this.name = name;
        this.sizeName = sizeName;
        this.sizeMl = sizeMl;
        this.basePriceCents = basePriceCents;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSizeName() { return sizeName; }
    public int getSizeMl() { return sizeMl; }
    public long getBasePriceCents() { return basePriceCents; }
}
