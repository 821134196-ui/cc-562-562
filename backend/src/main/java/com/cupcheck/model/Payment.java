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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** 本地模拟支付单 */
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    public Order order;

    public int amountCents;

    @Enumerated(EnumType.STRING)
    public PaymentStatus status = PaymentStatus.SUCCESS;

    /** MOCK_CASH / MOCK_QR，本地模拟 */
    public String method;

    public Instant paidAt = Instant.now();
}
