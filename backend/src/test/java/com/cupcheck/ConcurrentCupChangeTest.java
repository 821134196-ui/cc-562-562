package com.cupcheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

/**
 * 场景二：两名店员同时改杯。相同版本号并发提交 → 恰有一笔成功，
 * 另一笔 409；失败者按最新版本重试后生效，最终价格与最终容器选择一致。
 */
@QuarkusTest
class ConcurrentCupChangeTest extends BaseApiTest {

    @Test
    void 并发换杯_一人成功一人409_重试后价格与最终选择一致() throws Exception {
        // 中杯美式 1800
        JsonPath order = createOrder(1);
        long id = order.getLong("id");
        long v = order.getLong("version");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            // 店员A：切普通杯；店员B：自带杯核验通过。两人基于同一版本同时提交。
            Callable<Response> alice = () -> {
                ready.countDown();
                go.await();
                return storeCupRaw(id, v, "alice");
            };
            Callable<Response> bob = () -> {
                ready.countDown();
                go.await();
                return verifyCupRaw(id, v, 500, "CLEAN", "bob");
            };
            Future<Response> fa = pool.submit(alice);
            Future<Response> fb = pool.submit(bob);
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            Response ra = fa.get(30, TimeUnit.SECONDS);
            Response rb = fb.get(30, TimeUnit.SECONDS);

            List<Integer> statuses = List.of(ra.statusCode(), rb.statusCode());
            assertTrue(statuses.contains(200), "恰有一笔成功: " + statuses);
            assertTrue(statuses.contains(409), "另一笔版本冲突: " + statuses);

            // 失败方读取最新版本后重试 → 生效，成为最终选择
            boolean storeCupLost = ra.statusCode() == 409;
            long freshV = getOrder(id).getLong("version");
            Response retry = storeCupLost
                    ? storeCupRaw(id, freshV, "alice")
                    : verifyCupRaw(id, freshV, 500, "CLEAN", "bob");
            assertEquals(200, retry.statusCode());

            // 最终状态与最后一次写入一致，价格随之重算
            JsonPath end = getOrder(id);
            int subtotal = end.getInt("pricing.subtotalCents");
            int cupDiscount = end.getInt("pricing.cupDiscountCents");
            if (storeCupLost) {
                assertEquals("STORE_CUP", end.getString("container.type"));
                assertEquals(0, cupDiscount);
            } else {
                assertEquals("OWN_CUP", end.getString("container.type"));
                assertEquals(300, cupDiscount);
            }
            assertEquals(subtotal - cupDiscount, end.getInt("pricing.totalCents"));

            // 全程只有一条生效容器
            List<Boolean> actives = end.getList("containerHistory.findAll { it.active }.active");
            assertEquals(1, actives.size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 过期版本号直接拒绝() {
        JsonPath order = createOrder(1);
        long id = order.getLong("id");
        long stale = order.getLong("version");
        // 先改一次使版本前进
        storeCup(id, stale, "alice");
        // 再用旧版本改杯 → 409
        verifyCupRaw(id, stale, 500, "CLEAN", "bob").then().statusCode(409);
    }
}
