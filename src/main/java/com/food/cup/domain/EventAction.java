package com.food.cup.domain;

/** 订单变化时间线动作（append-only 审计）。 */
public enum EventAction {
    CREATED,
    /** 自带杯核验通过（含顾客中途换成合格自带杯） */
    CHECK_PASSED,
    /** 杯口不洁拒收，已切换普通杯 */
    RIM_REJECTED,
    /** 容量不足拒收，已切换普通杯 */
    CAPACITY_REJECTED,
    /** 顾客主动改用普通杯 */
    SWITCH_TO_DISPOSABLE,
    /** 饮品已制作后尝试换杯，被规则拒绝（容器保持原选择） */
    BLOCKED_AFTER_PREPARED,
    MARK_PREPARED,
    COUPON_APPLIED,
    COUPON_REMOVED,
    /** 叠加规则不允许：顾客改用合格自带杯后，不可叠加券被自动摘下并恢复未使用 */
    COUPON_AUTO_REMOVED_BY_RULE,
    PAID,
    REFUNDED,
    /** 退款后占用的优惠券被冲回，恢复为未使用 */
    COUPON_REVERTED_BY_REFUND
}
