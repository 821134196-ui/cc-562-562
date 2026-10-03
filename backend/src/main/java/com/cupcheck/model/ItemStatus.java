package com.cupcheck.model;

public enum ItemStatus {
    /** 尚未制作：改杯/拒收后立即按新容器重新计价 */
    PENDING,
    /** 已经制作：饮品已入杯，折扣金额锁定，不再随换杯变化 */
    MADE
}
