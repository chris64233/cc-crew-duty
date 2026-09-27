package com.chris64233.crewduty.api;

import org.springframework.http.HttpStatus;

/**
 * 业务异常：携带 HTTP 状态、错误码和明细，由全局异常处理器转换为统一错误结构。
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final java.util.List<String> details;

    public BusinessException(HttpStatus status, String code, String message) {
        this(status, code, message, java.util.List.of());
    }

    public BusinessException(HttpStatus status, String code, String message, java.util.List<String> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public java.util.List<String> getDetails() {
        return details;
    }

    public static BusinessException notFound(String what) {
        return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + "不存在");
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }

    public static BusinessException unprocessable(String code, String message, java.util.List<String> details) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, code, message, details);
    }
}
