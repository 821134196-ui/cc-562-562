package com.cupcheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;

/** 优惠券叠加规则：由券面 stackableWithCupDiscount 控制，不能重复抵扣 */
@QuarkusTest
class CouponStackingTest extends BaseApiTest {

    @Test
    void 不可叠加券与自带杯折扣互斥_可叠加券正常同享() {
        // 4200 分小计，自带杯 -600
        JsonPath order = createOrder(1, 3);
        long id = order.getLong("id");
        long v = verifyCup(id, order.getLong("version"), 500, "CLEAN", "小王").getLong("version");

        // VIP10 不可叠加 → 422
        applyCouponRaw(id, v, "VIP10").then().statusCode(422);

        // SAVE2 可叠加 → 4200 - 600 - 200 = 3400
        JsonPath after = applyCoupon(id, v, "SAVE2");
        assertEquals(600, after.getInt("pricing.cupDiscountCents"));
        assertEquals(200, after.getInt("pricing.couponDiscountCents"));
        assertEquals(3400, after.getInt("pricing.totalCents"));
    }

    @Test
    void 切回普通杯后不可叠加券恢复可用() {
        JsonPath order = createOrder(1, 3);
        long id = order.getLong("id");
        long v = verifyCup(id, order.getLong("version"), 500, "CLEAN", "小王").getLong("version");
        v = storeCup(id, v, "小王").getLong("version");

        // 无自带杯折扣，VIP10 九折生效：4200 × 10% = 420
        JsonPath after = applyCoupon(id, v, "VIP10");
        assertEquals(0, after.getInt("pricing.cupDiscountCents"));
        assertEquals(420, after.getInt("pricing.couponDiscountCents"));
        assertEquals(4200 - 420, after.getInt("pricing.totalCents"));
    }

    @Test
    void 未达最低消费不能用券() {
        // 单杯中杯美式 1800，FLAT8 最低消费 3000
        JsonPath order = createOrder(1);
        long id = order.getLong("id");
        applyCouponRaw(id, order.getLong("version"), "FLAT8").then().statusCode(422);
    }

    @Test
    void 折扣总额不超过小计() {
        // 单杯 1800，STACK3 减 300；再加自带杯 300 → 合计 600 < 1800，正常
        JsonPath order = createOrder(1);
        long id = order.getLong("id");
        long v = verifyCup(id, order.getLong("version"), 500, "CLEAN", "小王").getLong("version");
        JsonPath after = applyCoupon(id, v, "STACK3");
        assertEquals(1800 - 300 - 300, after.getInt("pricing.totalCents"));
        assertTrue(after.getInt("pricing.totalCents") >= 0);
    }
}
