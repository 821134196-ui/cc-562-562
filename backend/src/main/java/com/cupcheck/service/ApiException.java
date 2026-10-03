package com.cupcheck.service;

/** 业务异常：携带 HTTP 状态码与稳定错误码，由 ExceptionMapper 统一输出 */
public class ApiException extends RuntimeException {

    public final int status;
    public final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException notFound(String what) {
        return new ApiException(404, "NOT_FOUND", what + "不存在");
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, "BAD_REQUEST", message);
    }

    /** 订单状态不允许该操作（如已支付后改杯） */
    public static ApiException conflictState(String message) {
        return new ApiException(422, "ILLEGAL_STATE", message);
    }

    /** 乐观锁冲突：订单已被他人修改 */
    public static ApiException versionConflict() {
        return new ApiException(409, "VERSION_CONFLICT", "订单已被其他人修改，请刷新后重试");
    }
}
