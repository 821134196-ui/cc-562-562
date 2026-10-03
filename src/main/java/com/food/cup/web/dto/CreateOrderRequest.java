package com.food.cup.web.dto;

public record CreateOrderRequest(Long beverageId, Integer quantity, Integer personalCupMl) {
}
