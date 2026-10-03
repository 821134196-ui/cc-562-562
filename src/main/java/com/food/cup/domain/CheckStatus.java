package com.food.cup.domain;

/**
 * 杯具卫生/容量核验结果。
 * 普通杯无需核验（NOT_REQUIRED）；自带杯核验通过(PASSED)才可享受自带杯折扣。
 */
public enum CheckStatus {
    PENDING,
    PASSED,
    REJECTED,
    NOT_REQUIRED
}
