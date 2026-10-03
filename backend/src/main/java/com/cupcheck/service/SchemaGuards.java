package com.cupcheck.service;

import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * 数据库层硬约束（应用规则之外的第二道防线）：
 * 1. 部分唯一索引：同一订单最多一条 active 容器选择；
 * 2. 触发器：拒收记录禁止 UPDATE / DELETE，店员无法删除卫生拒收记录。
 */
@ApplicationScoped
public class SchemaGuards {

    private static final Logger LOG = Logger.getLogger(SchemaGuards.class);

    @Inject
    EntityManager em;

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        em.createNativeQuery("""
                CREATE UNIQUE INDEX IF NOT EXISTS one_active_container_per_order
                ON container_selection(order_id) WHERE active = 1
                """).executeUpdate();
        em.createNativeQuery("""
                CREATE TRIGGER IF NOT EXISTS rejection_record_no_update
                BEFORE UPDATE ON rejection_record
                BEGIN SELECT RAISE(ABORT, '拒收记录不可修改'); END
                """).executeUpdate();
        em.createNativeQuery("""
                CREATE TRIGGER IF NOT EXISTS rejection_record_no_delete
                BEFORE DELETE ON rejection_record
                BEGIN SELECT RAISE(ABORT, '拒收记录不可删除'); END
                """).executeUpdate();
        LOG.info("数据库约束就绪：单一有效容器索引 + 拒收记录不可变触发器");
    }
}
