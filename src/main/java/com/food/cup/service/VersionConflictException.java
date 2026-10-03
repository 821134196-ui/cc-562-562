package com.food.cup.service;

/** 客户端版本与服务端不一致（两人同时改杯等场景），映射 409。 */
public class VersionConflictException extends BusinessException {

    public VersionConflictException(long serverVersion) {
        super("VERSION_CONFLICT",
              "订单已被他人修改（服务端最新版本 " + serverVersion + "），请刷新后基于最新价格重试");
    }
}
