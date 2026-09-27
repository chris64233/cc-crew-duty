package com.chris64233.crewduty.service;

import java.util.List;

/**
 * 资格校验汇总报告。
 *
 * @param valid    全部规则是否通过
 * @param failures 全部失败原因（按校验发现顺序），valid 为 true 时为空
 */
public record QualificationReport(boolean valid, List<FailureReason> failures) {

    public static QualificationReport passed() {
        return new QualificationReport(true, List.of());
    }
}
