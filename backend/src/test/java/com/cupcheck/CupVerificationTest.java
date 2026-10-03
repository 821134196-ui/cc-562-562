package com.cupcheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;

/**
 * 场景一：自带杯核验（容量不足 / 杯口不洁 → 登记拒收并切换普通杯，价格立即重算）
 * 以及已制作/未制作饮品的差异化计价规则。
 */
@QuarkusTest
class CupVerificationTest extends BaseApiTest {

    @Test
    void 容量不足_登记拒收并自动改用普通杯_价格立即重算() {
        // 大杯美式 650ml / 2200 分
        JsonPath order = createOrder(2);
        long id = order.getLong("id");
        assertEquals(650, order.getInt("requiredCapacityMl"));
        assertEquals(2200, order.getInt("pricing.totalCents"));

        // 顾客杯子只有 500ml，卫生合格但容量不足
        JsonPath after = verifyCup(id, order.getLong("version"), 500, "CLEAN", "小王");

        // 自动切换普通杯，无自带杯折扣，价格 = 原价
        assertEquals("STORE_CUP", after.getString("container.type"));
        assertEquals("自带杯拒收，自动切换普通杯", after.getString("container.note"));
        assertEquals(0, after.getInt("pricing.cupDiscountCents"));
        assertEquals(2200, after.getInt("pricing.totalCents"));

        // 拒收记录已登记，含具体容量说明
        assertEquals(List.of("CAPACITY_INSUFFICIENT"), after.getList("rejections.code"));
        String reason = after.getString("rejections[0].reason");
        assertTrue(reason.contains("500ml") && reason.contains("650ml"), reason);
        assertEquals("小王", after.getString("rejections[0].operator"));

        // 核验记录留痕：容量不达标、未通过
        assertFalse(after.getBoolean("inspections[0].accepted"));
        assertFalse(after.getBoolean("inspections[0].capacityOk"));
        assertEquals(650, after.getInt("inspections[0].requiredCapacityMl"));
    }

    @Test
    void 杯口不洁_登记拒收并改用普通杯() {
        JsonPath order = createOrder(1);
        long id = order.getLong("id");

        JsonPath after = verifyCup(id, order.getLong("version"), 500, "DIRTY_RIM", "小李");

        assertEquals("STORE_CUP", after.getString("container.type"));
        assertEquals(List.of("DIRTY_RIM"), after.getList("rejections.code"));
        assertEquals("杯口不洁", after.getString("rejections[0].reason"));
        assertEquals(0, after.getInt("pricing.cupDiscountCents"));
        assertEquals(1800, after.getInt("pricing.totalCents"));
    }

    @Test
    void 自带杯核验通过_每件饮品享受折扣() {
        // 中杯美式 1800 + 中杯拿铁 2400 = 4200
        JsonPath order = createOrder(1, 3);
        long id = order.getLong("id");

        JsonPath after = verifyCup(id, order.getLong("version"), 500, "CLEAN", "小王");

        assertEquals("OWN_CUP", after.getString("container.type"));
        assertEquals(500, after.getInt("container.capacityMl"));
        // 每件未制作饮品减 300 分
        assertEquals(600, after.getInt("pricing.cupDiscountCents"));
        assertEquals(4200 - 600, after.getInt("pricing.totalCents"));
        // 容器历史：默认普通杯(失效) + 自带杯(生效)，全程只有一条生效
        List<Boolean> actives = after.getList("containerHistory.findAll { it.active }.active");
        assertEquals(1, actives.size());
        assertEquals(2, after.getList("containerHistory").size());
    }

    @Test
    void 换杯全程留痕_同一订单只有一条生效容器() {
        JsonPath order = createOrder(1);
        long id = order.getLong("id");
        long v = verifyCup(id, order.getLong("version"), 500, "CLEAN", "小王").getLong("version");
        v = storeCup(id, v, "小李").getLong("version");
        JsonPath after = verifyCup(id, v, 600, "CLEAN", "小王");

        assertEquals("OWN_CUP", after.getString("container.type"));
        assertEquals(600, after.getInt("container.capacityMl"));
        List<Boolean> actives = after.getList("containerHistory.findAll { it.active }.active");
        assertEquals(1, actives.size());
        // 默认普通杯 → 自带杯500 → 普通杯 → 自带杯600，共 4 条历史
        assertEquals(4, after.getList("containerHistory").size());
        assertEquals(300, after.getInt("pricing.cupDiscountCents"));
    }

    @Test
    void 已制作饮品锁定折扣_未制作饮品随换杯重算() {
        // 两杯中杯，自带杯核验通过
        JsonPath order = createOrder(1, 3);
        long id = order.getLong("id");
        long v = verifyCup(id, order.getLong("version"), 500, "CLEAN", "小王").getLong("version");

        // 第一杯制作完成 → 锁定 300 折扣
        JsonPath current = getOrder(id);
        long firstItem = current.getLong("items[0].id");
        JsonPath afterMade = markMade(id, firstItem, v);
        assertEquals("MADE", afterMade.getString("items[0].status"));

        // 随后杯子被判定杯口不洁 → 切普通杯：已制作的保留 300，未制作的折扣归零
        JsonPath afterReject = verifyCup(id, afterMade.getLong("version"), 500, "DIRTY_RIM", "小李");
        assertEquals("STORE_CUP", afterReject.getString("container.type"));
        assertEquals(300, afterReject.getInt("items[0].cupDiscountCents"));
        assertEquals(0, afterReject.getInt("items[1].cupDiscountCents"));
        assertEquals(300, afterReject.getInt("pricing.cupDiscountCents"));
        assertEquals(4200 - 300, afterReject.getInt("pricing.totalCents"));
    }

    @Test
    void 已支付订单不允许再改杯或用券() {
        JsonPath order = createOrder(1);
        long id = order.getLong("id");
        long v = pay(id, order.getLong("version"), "MOCK_CASH").getLong("version");

        verifyCupRaw(id, v, 500, "CLEAN", "小王").then().statusCode(422);
        storeCupRaw(id, v, "小王").then().statusCode(422);
        applyCouponRaw(id, v, "STACK3").then().statusCode(422);
    }
}
