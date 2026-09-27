package com.chris64233.crewduty.api;

import java.time.Instant;
import java.util.List;

/**
 * 统一错误结构：所有业务失败和参数校验失败都返回该结构。
 */
public record ErrorResponse(
        String code,
        String message,
        List<String> details,
        Instant timestamp
) {
    public static ErrorResponse of(String code, String message, List<String> details) {
        return new ErrorResponse(code, message, details == null ? List.of() : details, Instant.now());
    }
}
