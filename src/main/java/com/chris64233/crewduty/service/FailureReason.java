package com.chris64233.crewduty.service;

/**
 * 单条资格失败原因。
 *
 * @param rule            违反的规则
 * @param message         人类可读说明
 * @param memberEmployeeNo 相关成员工号（无法定位成员时为 null）
 * @param comboBizNo      相关组合业务号
 * @param segmentFlightNo 相关航班号（组合级问题为 null）
 */
public record FailureReason(
        FailureRule rule,
        String message,
        String memberEmployeeNo,
        String comboBizNo,
        String segmentFlightNo) {

    public static FailureReason segment(FailureRule rule, String message, AssignmentView seg) {
        return new FailureReason(rule, message, seg.memberEmployeeNo(),
                seg.sourceComboBizNo(), seg.flightNo());
    }

    public static FailureReason combo(FailureRule rule, String message, String comboBizNo) {
        return new FailureReason(rule, message, null, comboBizNo, null);
    }

    public static FailureReason rest(String message, AssignmentView first, AssignmentView second) {
        return new FailureReason(FailureRule.INSUFFICIENT_REST, message,
                second.memberEmployeeNo(), second.sourceComboBizNo(), second.flightNo());
    }
}
