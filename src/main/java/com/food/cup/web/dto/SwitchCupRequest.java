package com.food.cup.web.dto;

import com.food.cup.domain.ContainerType;

/**
 * 中途换杯。target=DISPOSABLE 改普通杯；target=PERSONAL 换自带杯（必须给容量并当场核验）。
 */
public record SwitchCupRequest(Long version,
                               ContainerType target,
                               Integer personalCupMl,
                               Boolean rimClean,
                               String note) {
}
