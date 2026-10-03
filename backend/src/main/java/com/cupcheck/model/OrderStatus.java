package com.cupcheck.model;

public enum OrderStatus {
    /** 点单中：可改杯、可加饮品、可用券 */
    OPEN,
    /** 已支付：容器与优惠锁定，可退款 */
    PAID,
    /** 已退款：终态 */
    REFUNDED
}
