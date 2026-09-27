package com.chris64233.crewduty.web;

import java.util.List;

/**
 * 服务层抛出的业务异常，由全局异常处理器统一转成 {@link ApiError}。
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient List<Object> details;

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public ApiException(ErrorCode errorCode, String message, List<Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = List.copyOf(details);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<Object> getDetails() {
        return details;
    }
}
