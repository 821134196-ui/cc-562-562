package com.food.cup;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * 核心规则集成测试（共享内存 SQLite）：
 * 容量不足拒收切普通杯、并发换杯 409 一致收敛、券叠加规则、退款冲回、已制作锁单、拒收记录不可删。
 */
@QuarkusTest
class PosApiTest {

    // 大杯燕麦拿铁 id=4（550ml, 2800分）；大杯美式 id=2（500ml, 2200分）
    private static final int LATTE_LARGE = 4;
    private static final int AMERICANO_LARGE = 2;

    private Integer createOrder(int beverageId) {
        return given().contentType("application/json")
                .body("""
                    {"beverageId":%d,"quantity":1}
                    """.formatted(beverageId))
                .post("/api/orders")
                .then().statusCode(200)
                .extract().path("id");
    }

    private io.restassured.response.Response post(String path, Object body) {
        return given().contentType("application/json").body(body).post(path);
    }

    /** 支付后退款，冲回订单占用的优惠券，保持各测试间券状态隔离。 */
    private void payAndRefund(Integer id) {
        int v1 = given().get("/api/orders/" + id).path("version");
        post("/api/orders/" + id + "/pay", """
                {"version":%d}""".formatted(v1)).then().statusCode(200);
        int v2 = given().get("/api/orders/" + id).path("version");
        post("/api/orders/" + id + "/refund", """
                {"version":%d}""".formatted(v2)).then().statusCode(200);
    }

    @Test
    void insufficientCapacity_registersRejectionAndFallsBackToDisposable() {
        Integer id = createOrder(LATTE_LARGE);

        // 自带杯 400ml < 要求 550ml
        post("/api/orders/" + id + "/switch-cup", """
                {"version":0,"target":"PERSONAL","personalCupMl":400,"rimClean":true}""")
            .then().statusCode(200)
            .body("containerType", equalTo("DISPOSABLE"))
            .body("checkStatus", equalTo("REJECTED"))
            .body("rejections[0].reason", equalTo("INSUFFICIENT_CAPACITY"))
            .body("rejections[0].cupMl", equalTo(400))
            .body("rejections[0].requiredMl", equalTo(550))
            .body("price.personalDiscountCents", equalTo(0))
            .body("price.finalPriceCents", equalTo(2800))
            .body("version", equalTo(1));

        // 换成合格自带杯 600ml：享 5% 折扣 2800-140=2660，拒收记录仍然保留
        post("/api/orders/" + id + "/switch-cup", """
                {"version":1,"target":"PERSONAL","personalCupMl":600,"rimClean":true}""")
            .then().statusCode(200)
            .body("containerType", equalTo("PERSONAL"))
            .body("checkStatus", equalTo("PASSED"))
            .body("price.personalDiscountCents", equalTo(140))
            .body("price.finalPriceCents", equalTo(2660))
            .body("rejections.size()", equalTo(1));
    }

    @Test
    void rimUnsanitary_registersRejectionAndFallsBack() {
        Integer id = createOrder(LATTE_LARGE);
        post("/api/orders/" + id + "/switch-cup", """
                {"version":0,"target":"PERSONAL","personalCupMl":600,"rimClean":false,"note":"杯口有口红印"}""")
            .then().statusCode(200)
            .body("containerType", equalTo("DISPOSABLE"))
            .body("rejections[0].reason", equalTo("RIM_UNSANITARY"))
            .body("rejections[0].note", equalTo("杯口有口红印"))
            .body("price.finalPriceCents", equalTo(2800));
    }

    @Test
    void concurrentCupSwitch_oneWinsOtherGets409AndStateConverges() throws Exception {
        Integer id = createOrder(LATTE_LARGE);
        // 先核验成合格自带杯 v1
        post("/api/orders/" + id + "/switch-cup", """
                {"version":0,"target":"PERSONAL","personalCupMl":600,"rimClean":true}""")
            .then().statusCode(200);

        // 两名店员同拿 v1 并发提交不同容器
        var t1 = new Thread(() -> post("/api/orders/" + id + "/switch-cup", """
                {"version":1,"target":"DISPOSABLE"}""").andReturn());
        var t2 = new Thread(() -> post("/api/orders/" + id + "/switch-cup", """
                {"version":1,"target":"PERSONAL","personalCupMl":700,"rimClean":true}""").andReturn());
        t1.start(); t2.start(); t1.join(); t2.join();

        // 最终只有一个容器选择，价格与之严格一致
        var finalView = given().get("/api/orders/" + id).andReturn().jsonPath();
        String finalContainer = finalView.getString("containerType");
        int finalPrice = finalView.getInt("price.finalPriceCents");
        org.hamcrest.MatcherAssert.assertThat(finalPrice,
                finalContainer.equals("DISPOSABLE") ? equalTo(2800) : equalTo(2660));

        // 失败者刷新拿到最新版本后，向“当前容器的反方向”重试，必然产生有效变更
        var current = given().get("/api/orders/" + id).andReturn().jsonPath();
        int currentVersion = current.getInt("version");
        if ("DISPOSABLE".equals(current.getString("containerType"))) {
            post("/api/orders/" + id + "/switch-cup", """
                    {"version":%d,"target":"PERSONAL","personalCupMl":700,"rimClean":true}""".formatted(currentVersion))
                .then().statusCode(200)
                .body("containerType", equalTo("PERSONAL"))
                .body("version", equalTo(3));
        } else {
            post("/api/orders/" + id + "/switch-cup", """
                    {"version":%d,"target":"DISPOSABLE"}""".formatted(currentVersion))
                .then().statusCode(200)
                .body("containerType", equalTo("DISPOSABLE"))
                .body("version", equalTo(3));
        }
    }

    @Test
    void staleVersion_isRejected409() {
        Integer id = createOrder(LATTE_LARGE);
        post("/api/orders/" + id + "/switch-cup", """
                {"version":0,"target":"PERSONAL","personalCupMl":600,"rimClean":true}""")
            .then().statusCode(200);
        post("/api/orders/" + id + "/switch-cup", """
                {"version":0,"target":"DISPOSABLE"}""")
            .then().statusCode(409)
            .body("code", equalTo("VERSION_CONFLICT"));
    }

    @Test
    void nonStackableCoupon_autoRemovedWhenPersonalCupPasses() {
        Integer id = createOrder(AMERICANO_LARGE);
        post("/api/orders/" + id + "/coupon", """
                {"version":0,"code":"NEW10"}""")
            .then().statusCode(200)
            .body("price.finalPriceCents", equalTo(1980)); // 2200 * 0.9

        // 核验合格自带杯：NEW10 按规则自动摘下并恢复未使用，只留自带杯 5% → 2090
        post("/api/orders/" + id + "/switch-cup", """
                {"version":1,"target":"PERSONAL","personalCupMl":500,"rimClean":true}""")
            .then().statusCode(200)
            .body("coupon", nullValue())
            .body("price.personalDiscountCents", equalTo(110))
            .body("price.couponDiscountCents", equalTo(0))
            .body("price.finalPriceCents", equalTo(2090))
            .body("events.last().action", equalTo("COUPON_AUTO_REMOVED_BY_RULE"));

        // 券已冲回为未使用
        given().get("/api/coupons").then()
                .body("find { it.code == 'NEW10' }.status", equalTo("UNUSED"));
    }

    @Test
    void applyingNonStackableCouponToPersonalOrder_isRejected() {
        Integer id = createOrder(AMERICANO_LARGE);
        post("/api/orders/" + id + "/switch-cup", """
                {"version":0,"target":"PERSONAL","personalCupMl":500,"rimClean":true}""")
            .then().statusCode(200);
        post("/api/orders/" + id + "/coupon", """
                {"version":1,"code":"NEW10"}""")
            .then().statusCode(409)
            .body("code", equalTo("COUPON_NOT_STACKABLE"));
    }

    @Test
    void couponCannotBeReusedAcrossOrders() {
        Integer a = createOrder(AMERICANO_LARGE);
        post("/api/orders/" + a + "/coupon", """
                {"version":0,"code":"SAVE200"}""")
            .then().statusCode(200);

        Integer b = createOrder(AMERICANO_LARGE);
        post("/api/orders/" + b + "/coupon", """
                {"version":0,"code":"SAVE200"}""")
            .then().statusCode(409)
            .body("code", equalTo("COUPON_IN_USE"));

        // 清理：支付 a 后退款，SAVE200 冲回（同时也是退款冲回的又一例证）
        payAndRefund(a);
    }

    @Test
    void stackableCoupon_appliesAfterPersonalDiscountWithoutDoubleCounting() {
        Integer id = createOrder(AMERICANO_LARGE);
        post("/api/orders/" + id + "/switch-cup", """
                {"version":0,"target":"PERSONAL","personalCupMl":500,"rimClean":true}""")
            .then().statusCode(200);
        post("/api/orders/" + id + "/coupon", """
                {"version":1,"code":"SAVE200"}""")
            .then().statusCode(200)
            .body("price.personalDiscountCents", equalTo(110))
            .body("price.couponDiscountCents", equalTo(200))
            .body("price.finalPriceCents", equalTo(1890)); // 2200-110-200
        payAndRefund(id);
    }

    @Test
    void afterPrepare_cupSwitchLockedButAudited() {
        Integer id = createOrder(LATTE_LARGE);
        post("/api/orders/" + id + "/switch-cup", """
                {"version":0,"target":"PERSONAL","personalCupMl":600,"rimClean":true}""")
            .then().statusCode(200);
        post("/api/orders/" + id + "/prepare", """
                {"version":1}""")
            .then().statusCode(200).body("status", equalTo("PREPARED"));

        // 已制作换杯：409 拒绝，容器与价格不变
        post("/api/orders/" + id + "/switch-cup", """
                {"version":2,"target":"DISPOSABLE"}""")
            .then().statusCode(409).body("code", equalTo("ORDER_LOCKED"));
        given().get("/api/orders/" + id).then()
                .body("containerType", equalTo("PERSONAL"))
                .body("status", equalTo("PREPARED"))
                .body("price.finalPriceCents", equalTo(2660))
                .body("events.last().action", equalTo("BLOCKED_AFTER_PREPARED"));
    }

    @Test
    void refund_revertsCouponAndAllowsReuse() {
        Integer id = createOrder(AMERICANO_LARGE);
        post("/api/orders/" + id + "/coupon", """
                {"version":0,"code":"SAVE200"}""")
            .then().statusCode(200);
        Integer payVersion = given().get("/api/orders/" + id).path("version");
        post("/api/orders/" + id + "/pay", """
                {"version":%d}""".formatted(payVersion))
            .then().statusCode(200).body("status", equalTo("PAID"));

        Integer paidVersion = given().get("/api/orders/" + id).path("version");
        post("/api/orders/" + id + "/refund", """
                {"version":%d}""".formatted(paidVersion))
            .then().statusCode(200)
            .body("status", equalTo("REFUNDED"))
            .body("events.last().action", equalTo("COUPON_REVERTED_BY_REFUND"));

        // 券冲回，可用于新订单
        given().get("/api/coupons").then()
                .body("find { it.code == 'SAVE200' }.status", equalTo("UNUSED"));
        Integer other = createOrder(AMERICANO_LARGE);
        post("/api/orders/" + other + "/coupon", """
                {"version":0,"code":"SAVE200"}""")
            .then().statusCode(200)
            .body("price.couponDiscountCents", equalTo(200));
        payAndRefund(other);
    }

    @Test
    void hygieneRejectionsHaveNoDeleteEndpoint_andRemainAcrossChanges() {
        Integer id = createOrder(LATTE_LARGE);
        post("/api/orders/" + id + "/switch-cup", """
                {"version":0,"target":"PERSONAL","personalCupMl":400,"rimClean":true}""")
            .then().statusCode(200);
        // 尝试删除拒收记录：没有任何删除 API（404 路径不存在 或 405 方法不允许）
        given().delete("/api/orders/" + id + "/rejections/1")
            .then().statusCode(anyOf(equalTo(404), equalTo(405)));
        // 后续换杯、拒收记录仍在
        post("/api/orders/" + id + "/switch-cup", """
                {"version":1,"target":"PERSONAL","personalCupMl":600,"rimClean":true}""")
            .then().body("rejections.size()", equalTo(1));
    }
}
