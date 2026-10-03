package com.food.cup.service;

import jakarta.ws.rs.core.Response;

/** 业务规则违反（状态不允许、券不可用等），映射为 422。 */
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }

    public Response.Status status() { return Response.Status.CONFLICT; }
}
