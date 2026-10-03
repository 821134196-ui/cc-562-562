package com.food.cup.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 订单变化时间线（换杯、拒收、券变化、支付、退款……），append-only。 */
@Entity
@Table(name = "order_event")
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private PosOrder order;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventAction action;

    @Column(length = 500)
    private String detail;

    protected OrderEvent() {
    }

    public OrderEvent(PosOrder order, EventAction action, String detail) {
        this.order = order;
        this.action = action;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public EventAction getAction() { return action; }
    public String getDetail() { return detail; }
}
