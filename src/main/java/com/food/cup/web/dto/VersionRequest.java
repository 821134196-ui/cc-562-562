package com.food.cup.web.dto;

/** 只携带乐观锁版本的请求体（移除券/标记制作/支付/退款）。 */
public record VersionRequest(Long version) {
}
