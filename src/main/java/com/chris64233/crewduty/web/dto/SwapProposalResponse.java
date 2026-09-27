package com.chris64233.crewduty.web.dto;

import com.chris64233.crewduty.domain.SwapProposal;
import java.time.Instant;
import java.util.List;

/**
 * 交换方案详情（提交和确认共用）。
 */
public record SwapProposalResponse(
        Long id,
        String bizNo,
        String status,
        String comboABizNo,
        String comboBBizNo,
        long comboAVersion,
        long comboBVersion,
        List<ItemView> items,
        Instant proposedAt,
        Instant confirmedAt,
        Long resultingComboAVersion,
        Long resultingComboBVersion) {

    public static SwapProposalResponse from(SwapProposal p) {
        return new SwapProposalResponse(
                p.getId(), p.getBizNo(), p.getStatus().name(),
                p.getComboA().getBizNo(), p.getComboB().getBizNo(),
                p.getComboAVersion(), p.getComboBVersion(),
                p.getItems().stream().map(ItemView::new).toList(),
                p.getProposedAt(), p.getConfirmedAt(),
                p.getResultingComboAVersion(), p.getResultingComboBVersion());
    }

    public record ItemView(String side, Long segmentId, String targetEmployeeNo) {
        ItemView(com.chris64233.crewduty.domain.SwapItem item) {
            this(item.getSide(), item.getSegmentId(), item.getTargetEmployeeNo());
        }
    }
}
