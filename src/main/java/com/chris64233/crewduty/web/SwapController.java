package com.chris64233.crewduty.web;

import com.chris64233.crewduty.service.CrewDutyService;
import com.chris64233.crewduty.web.dto.SwapProposalRequest;
import com.chris64233.crewduty.web.dto.SwapProposalResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 成员交换接口：提交方案、确认（幂等）、查询方案。
 */
@RestController
@RequestMapping("/api/swaps")
public class SwapController {

    private final CrewDutyService dutyService;

    public SwapController(CrewDutyService dutyService) {
        this.dutyService = dutyService;
    }

    /** 提交交换方案，冻结双方组合版本；bizNo 同时用于确认幂等。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SwapProposalResponse propose(@Valid @RequestBody SwapProposalRequest request) {
        return dutyService.proposeSwap(request);
    }

    @GetMapping("/{bizNo}")
    public SwapProposalResponse detail(@PathVariable String bizNo) {
        return dutyService.getProposal(bizNo);
    }

    /**
     * 确认交换：重新校验双方完整组合并在一次事务中完成。
     * 任一组合版本变化或重新校验失败，方案作废（409/422）。
     */
    @PostMapping("/{bizNo}/confirm")
    public SwapProposalResponse confirm(@PathVariable String bizNo) {
        return dutyService.confirmSwap(bizNo);
    }
}
