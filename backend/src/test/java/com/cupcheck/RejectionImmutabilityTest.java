package com.cupcheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * 卫生拒收记录不可被店员删除或篡改：
 * 应用层不提供任何修改入口，数据库触发器直接拒绝 DELETE/UPDATE。
 */
@QuarkusTest
class RejectionImmutabilityTest extends BaseApiTest {

    @Inject
    EntityManager em;

    @Test
    void 拒收记录禁止删除和修改() {
        JsonPath order = createOrder(1);
        long id = order.getLong("id");
        JsonPath after = verifyCup(id, order.getLong("version"), 500, "DIRTY_RIM", "小王");
        assertEquals(1, after.getList("rejections").size());

        // 直接 DELETE → 触发器拒绝
        assertThrows(Exception.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("DELETE FROM rejection_record").executeUpdate();
            em.flush();
        }));

        // 直接 UPDATE → 触发器拒绝
        assertThrows(Exception.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("UPDATE rejection_record SET reason = '篡改'").executeUpdate();
            em.flush();
        }));

        // 记录原样保留
        JsonPath end = getOrder(id);
        assertEquals(1, end.getList("rejections").size());
        assertEquals("杯口不洁", end.getString("rejections[0].reason"));
    }
}
