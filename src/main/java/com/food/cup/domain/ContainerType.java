package com.food.cup.domain;

/**
 * 容器类型：顾客自带杯 / 门店普通杯（一次性杯）。
 * 同一订单同一时刻只能保留一个有效选择，见 {@link PosOrder#getContainerType()}。
 */
public enum ContainerType {
    PERSONAL,
    DISPOSABLE
}
