package com.chris64233.crewduty.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 提交交换方案请求。
 *
 * <p>方案作用于两个已发布组合 {@code comboABizNo} / {@code comboBBizNo}；
 * 每个交换项指定哪一方（"A"/"B"）的哪个段（用段序号定位）在确认后改由
 * 目标成员工号执飞。确认时复用同一 {@code bizNo} 保证幂等。
 */
public record SwapProposalRequest(
        @NotBlank String bizNo,
        @NotBlank String comboABizNo,
        @NotBlank String comboBBizNo,
        @NotEmpty List<@Valid SwapItemRequest> items) {

    public record SwapItemRequest(
            @NotBlank String side,
            int segmentSeqNo,
            @NotBlank String targetEmployeeNo) {
    }
}
