package com.food.cup.web.dto;

/**
 * 杯具核验请求。version 为店员当前看到的订单版本（乐观锁）。
 */
public record InspectRequest(Long version, int cupMl, boolean rimClean, String note) {
}
