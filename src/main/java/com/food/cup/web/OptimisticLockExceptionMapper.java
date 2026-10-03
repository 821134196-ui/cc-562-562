package com.food.cup.web;

import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/**
 * JPA 乐观锁兜底：两名店员同时改杯且都拿着同一旧版本时，
 * 后提交者的 UPDATE ... WHERE version=? 影响行数为 0，在此转成 409。
 */
@Provider
public class OptimisticLockExceptionMapper implements ExceptionMapper<OptimisticLockException> {

    @Override
    public Response toResponse(OptimisticLockException e) {
        return Response.status(Response.Status.CONFLICT)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of(
                        "code", "VERSION_CONFLICT",
                        "message", "订单刚刚已被他人修改并提交，请刷新获取最新价格与容器选择后重试"))
                .build();
    }
}
