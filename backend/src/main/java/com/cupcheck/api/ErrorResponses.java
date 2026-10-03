package com.cupcheck.api;

import com.cupcheck.service.ApiException;

import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.hibernate.StaleObjectStateException;
import org.hibernate.exception.LockAcquisitionException;

/** 统一错误输出：{ "code": "...", "message": "..." } */
public final class ErrorResponses {

    private ErrorResponses() {
    }

    public record ErrorBody(String code, String message) {
    }

    static Response body(int status, String code, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON)
                .entity(new ErrorBody(code, message)).build();
    }

    /**
     * 判断异常链是否属于“并发冲突”：
     * 乐观锁失败，或 SQLite 在 WAL 下对并发写者 fail-fast 的 BUSY/LOCKED。
     * 这类错误对客户端都意味着：刷新后重试 → HTTP 409。
     */
    static boolean isConcurrencyConflict(Throwable t) {
        for (Throwable e = t; e != null; e = e.getCause()) {
            if (e instanceof OptimisticLockException
                    || e instanceof StaleObjectStateException
                    || e instanceof LockAcquisitionException
                    || e instanceof jakarta.persistence.PessimisticLockException
                    || e instanceof jakarta.persistence.LockTimeoutException) {
                return true;
            }
            String msg = e.getMessage();
            if (msg != null) {
                String m = msg.toLowerCase();
                if (m.contains("database is locked") || m.contains("sqlite_busy")
                        || m.contains("sqlite_locked") || m.contains("deadlock")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Provider
    public static class Api implements ExceptionMapper<ApiException> {
        @Override
        public Response toResponse(ApiException e) {
            return body(e.status, e.code, e.getMessage());
        }
    }

    /** 乐观锁提交期冲突（两人同时改杯，后提交者）→ 409 */
    @Provider
    public static class OptimisticLock implements ExceptionMapper<OptimisticLockException> {
        @Override
        public Response toResponse(OptimisticLockException e) {
            return body(409, "VERSION_CONFLICT", "订单已被其他人修改，请刷新后重试");
        }
    }

    @Provider
    public static class StaleState implements ExceptionMapper<StaleObjectStateException> {
        @Override
        public Response toResponse(StaleObjectStateException e) {
            return body(409, "VERSION_CONFLICT", "订单已被其他人修改，请刷新后重试");
        }
    }

    /** SQLite 写锁冲突 → 409，提示重试 */
    @Provider
    public static class LockTimeout implements ExceptionMapper<LockAcquisitionException> {
        @Override
        public Response toResponse(LockAcquisitionException e) {
            return body(409, "LOCKED", "订单正被其他人修改，请刷新后重试");
        }
    }

    @Provider
    public static class Fallback implements ExceptionMapper<Exception> {
        @Override
        public Response toResponse(Exception e) {
            if (isConcurrencyConflict(e)) {
                return body(409, "VERSION_CONFLICT", "订单已被其他人修改，请刷新后重试");
            }
            return body(500, "INTERNAL", e.getMessage() == null ? "内部错误" : e.getMessage());
        }
    }
}
