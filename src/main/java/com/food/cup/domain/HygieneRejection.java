package com.food.cup.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 卫生拒收记录。append-only：系统只提供新增（拒收登记），
 * 不提供任何修改或删除入口，店员无法删除；即使后来换杯/退款，记录依然保留。
 */
@Entity
@Table(name = "hygiene_rejection")
public class HygieneRejection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private PosOrder order;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RejectReason reason;

    /** 拒收时登记的自带杯容量（毫升） */
    @Column(nullable = false)
    private int cupMl;

    /** 饮品规格要求的容量（毫升） */
    @Column(nullable = false)
    private int requiredMl;

    @Column
    private String note;

    protected HygieneRejection() {
    }

    public HygieneRejection(PosOrder order, RejectReason reason, int cupMl, int requiredMl, String note) {
        this.order = order;
        this.reason = reason;
        this.cupMl = cupMl;
        this.requiredMl = requiredMl;
        this.note = note;
    }

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public RejectReason getReason() { return reason; }
    public int getCupMl() { return cupMl; }
    public int getRequiredMl() { return requiredMl; }
    public String getNote() { return note; }
}
