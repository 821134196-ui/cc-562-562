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
 * 卫生/容量拒收记录。只增不改不删：
 * 应用层不提供任何修改入口，数据库层由触发器拒绝 UPDATE/DELETE。
 */
@Entity
@Table(name = "rejection_record")
public class RejectionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    public Order order;

    /** 触发拒收的核验记录；系统自动判拒（如加饮品后容量不足）时为空 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id")
    public CupInspection inspection;

    @Enumerated(EnumType.STRING)
    public RejectionCode code;

    public String reason;

    public String operator;

    public Instant createdAt = Instant.now();

    public static RejectionRecord of(Order order, CupInspection inspection, RejectionCode code,
            String reason, String operator) {
        RejectionRecord r = new RejectionRecord();
        r.order = order;
        r.inspection = inspection;
        r.code = code;
        r.reason = reason;
        r.operator = operator;
        return r;
    }
}
