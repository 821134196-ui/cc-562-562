package com.food.cup.domain;

/**
 * 订单状态。
 * CREATED 未制作（可自由换杯、改券、重算）；PREPARED 饮品已制作（容器锁定）；
 * PAID 已支付；REFUNDED 已退款。
 */
public enum OrderStatus {
    CREATED,
    PREPARED,
    PAID,
    REFUNDED
}
