package com.chris64233.crewduty.web;

import java.util.List;

/**
 * 全服务统一错误结构。
 *
 * @param code    机器可判读错误码（{@link ErrorCode} 名称）
 * @param message 面向调用方的错误概要
 * @param details 结构化明细：字段校验错误、资格失败原因列表等
 * @param traceId 关联本次请求的追踪标识（未配置时为 null）
 */
public record ApiError(String code, String message, List<Object> details, String traceId) {

    public static ApiError of(ErrorCode code, String message, List<Object> details) {
        return new ApiError(code.name(), message, details, null);
    }

    public static ApiError of(ErrorCode code, String message, List<Object> details, String traceId) {
        return new ApiError(code.name(), message, details, traceId);
    }
}
