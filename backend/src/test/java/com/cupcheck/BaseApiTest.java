package com.cupcheck;

import static io.restassured.RestAssured.given;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

/** 测试共用的 API 小助手 */
abstract class BaseApiTest {

    static JsonPath createOrder(long... drinkSizeIds) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (long id : drinkSizeIds) {
            items.add(Map.of("drinkSizeId", id, "quantity", 1));
        }
        return given().contentType(ContentType.JSON).body(Map.of("items", items))
                .when().post("/api/orders")
                .then().statusCode(201).extract().jsonPath();
    }

    static JsonPath getOrder(long id) {
        return given().when().get("/api/orders/" + id)
                .then().statusCode(200).extract().jsonPath();
    }

    static Response verifyCupRaw(long id, long version, int capacityMl, String hygiene, String by) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("expectedVersion", version, "capacityMl", capacityMl,
                        "hygiene", hygiene, "inspector", by, "changedBy", by))
                .when().post("/api/orders/" + id + "/container/own-cup/verify");
    }

    static JsonPath verifyCup(long id, long version, int capacityMl, String hygiene, String by) {
        return verifyCupRaw(id, version, capacityMl, hygiene, by)
                .then().statusCode(200).extract().jsonPath();
    }

    static Response storeCupRaw(long id, long version, String by) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("expectedVersion", version, "changedBy", by))
                .when().post("/api/orders/" + id + "/container/store-cup");
    }

    static JsonPath storeCup(long id, long version, String by) {
        return storeCupRaw(id, version, by).then().statusCode(200).extract().jsonPath();
    }

    static Response applyCouponRaw(long id, long version, String code) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("expectedVersion", version, "code", code))
                .when().post("/api/orders/" + id + "/coupon");
    }

    static JsonPath applyCoupon(long id, long version, String code) {
        return applyCouponRaw(id, version, code).then().statusCode(200).extract().jsonPath();
    }

    static JsonPath markMade(long orderId, long itemId, long version) {
        return given().contentType(ContentType.JSON).body(Map.of("expectedVersion", version))
                .when().post("/api/orders/" + orderId + "/items/" + itemId + "/made")
                .then().statusCode(200).extract().jsonPath();
    }

    static JsonPath pay(long id, long version, String method) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("expectedVersion", version, "method", method))
                .when().post("/api/orders/" + id + "/pay")
                .then().statusCode(200).extract().jsonPath();
    }

    static JsonPath refund(long id, String reason) {
        return given().contentType(ContentType.JSON).body(Map.of("reason", reason))
                .when().post("/api/orders/" + id + "/refund")
                .then().statusCode(200).extract().jsonPath();
    }

    static JsonPath coupons() {
        return given().when().get("/api/coupons").then().statusCode(200).extract().jsonPath();
    }

    static String couponStatus(String code) {
        return coupons().getString("find { it.code == '" + code + "' }.status");
    }
}
