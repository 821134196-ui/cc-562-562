package com.food.cup.domain;

public enum CouponType {
    /** 按百分比折扣，value=百分比（如 10 表示 9 折） */
    PERCENT,
    /** 立减固定金额，value=分 */
    FIXED
}
