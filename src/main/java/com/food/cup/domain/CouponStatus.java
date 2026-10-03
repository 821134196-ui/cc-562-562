package com.food.cup.domain;

public enum CouponStatus {
    /** 未使用，可应用到订单 */
    UNUSED,
    /** 已被某订单占用（支付后），不能重复抵扣 */
    USED
}
