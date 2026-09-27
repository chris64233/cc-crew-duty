package com.chris64233.crewduty.web.dto;

import com.chris64233.crewduty.domain.AuditAction;
import com.chris64233.crewduty.domain.AuditRecord;
import java.time.Instant;
import java.util.List;

/**
 * 审计记录响应。
 */
public record AuditResponse(
        Long id,
        AuditAction action,
        String bizNo,
        String comboIds,
        Instant occurredAt,
        String detail) {

    public static AuditResponse from(AuditRecord r) {
        return new AuditResponse(r.getId(), r.getAction(), r.getBizNo(), r.getComboIds(),
                r.getOccurredAt(), r.getDetail());
    }

    public record ListResponse(List<AuditResponse> records) {
    }
}
