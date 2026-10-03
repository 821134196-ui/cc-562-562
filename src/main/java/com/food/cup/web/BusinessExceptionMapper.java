package com.food.cup.web;

import com.food.cup.service.BusinessException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/** 业务异常统一返回 JSON：{code,message}，HTTP 状态默认 409。 */
@Provider
public class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {

    @Override
    public Response toResponse(BusinessException e) {
        return Response.status(e.status())
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("code", e.getCode(), "message", e.getMessage()))
                .build();
    }
}
