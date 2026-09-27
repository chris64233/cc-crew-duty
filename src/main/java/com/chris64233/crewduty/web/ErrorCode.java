package com.chris64233.crewduty.web;

/**
 * 统一错误码。错误响应 {@link ApiError} 始终携带机器可判读的 code 与 HTTP 状态码。
 */
public enum ErrorCode {
    /** 请求参数不合法（Bean Validation 失败、时间区间错误等） */
    VALIDATION_ERROR(400),
    /** 业务号相同但请求内容不同 */
    IDEMPOTENT_CONFLICT(409),
    /** 发布/交换的资格规则校验失败 */
    QUALIFICATION_FAILED(422),
    /** 引用的成员、组合或交换方案不存在 */
    NOT_FOUND(404),
    /** 交换方案基于的组合版本已变化（并发交换/排班变更） */
    CONCURRENT_MODIFICATION(409),
    /** 交换方案已确认，不能重复提交或修改 */
    SWAP_ALREADY_CONFIRMED(409),
    /** 交换方案已作废（版本过期或确认时校验失败），不能再确认 */
    SWAP_REJECTED(409),
    /** 交换方案内容不合法（双方组合相同、交换项为空等） */
    SWAP_INVALID(400),
    /** 发布业务号已存在（并发首次写入冲突） */
    BIZ_NO_TAKEN(409),
    /** 落库阶段的乐观锁失败 */
    STALE_STATE(409),
    /** 成员工号已存在 */
    MEMBER_ALREADY_EXISTS(409),
    /** 未预期的服务端错误 */
    INTERNAL_ERROR(500);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
