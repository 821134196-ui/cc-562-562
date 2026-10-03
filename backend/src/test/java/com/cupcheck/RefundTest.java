package com.cupcheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;

/**
 * 场景三：支付后退款 —— 支付单冲回、订单置为已退款，
 * 自带杯折扣与优惠券抵扣金额被冲回记录，优惠券恢复为可再次使用。
 */
@QuarkusTest
class RefundTest extends BaseApiTest {

    @Test
    void 退款后_优惠正确冲回_优惠券恢复可用() {
        // 中杯美式 1800 + 中杯拿铁 2400 = 4200；自带杯 -600；SAVE5 可叠加 -500 → 应收 3100
        JsonPath order = createOrder(1, 3);
        long id = order.getLong("id");
        long v = verifyCup(id, order.getLong("version"), 500, "CLEAN", "小王").getLong("version");
        v = applyCoupon(id, v, "SAVE5").getLong("version");

        JsonPath paid = pay(id, v, "MOCK_QR");
        assertEquals("PAID", paid.getString("status"));
        assertEquals(3100, paid.getInt("payment.amountCents"));
        assertEquals("MOCK_QR", paid.getString("payment.method"));
        assertEquals("USED", couponStatus("SAVE5"));

        // 退款
        JsonPath refunded = refund(id, "顾客临时有事");
        assertEquals("REFUNDED", refunded.getString("status"));
        assertEquals("REFUNDED", refunded.getString("payment.status"));

        // 退款单：按实收金额退回，冲回两笔优惠，优惠券恢复
        assertEquals(3100, refunded.getInt("refunds[0].amountCents"));
        assertEquals(600, refunded.getInt("refunds[0].reversedCupDiscountCents"));
        assertEquals(500, refunded.getInt("refunds[0].reversedCouponDiscountCents"));
        assertTrue(refunded.getBoolean("refunds[0].couponRestored"));
        assertEquals("ACTIVE", couponStatus("SAVE5"));

        // 恢复后的优惠券可在新订单再次使用
        JsonPath order2 = createOrder(1, 3);
        JsonPath reapplied = applyCoupon(order2.getLong("id"), order2.getLong("version"), "SAVE5");
        assertEquals(500, reapplied.getInt("pricing.couponDiscountCents"));
        assertEquals(4200 - 500, reapplied.getInt("pricing.totalCents"));
    }

    @Test
    void 未支付订单不能退款_已退款订单不能重复退款() {
        JsonPath order = createOrder(1);
        long id = order.getLong("id");

        // 未支付 → 422
        io.restassured.response.Response r1 = io.restassured.RestAssured.given()
                .contentType(io.restassured.http.ContentType.JSON)
                .body(java.util.Map.of("reason", "x"))
                .post("/api/orders/" + id + "/refund");
        org.junit.jupiter.api.Assertions.assertEquals(422, r1.statusCode());

        long v = pay(id, getOrder(id).getLong("version"), "MOCK_CASH").getLong("version");
        refund(id, "第一次");
        // 重复退款 → 422
        io.restassured.response.Response r2 = io.restassured.RestAssured.given()
                .contentType(io.restassured.http.ContentType.JSON)
                .body(java.util.Map.of("reason", "第二次"))
                .post("/api/orders/" + id + "/refund");
        org.junit.jupiter.api.Assertions.assertEquals(422, r2.statusCode());
    }
}
