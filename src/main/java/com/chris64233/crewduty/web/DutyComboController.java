package com.chris64233.crewduty.web;

import com.chris64233.crewduty.service.CrewDutyService;
import com.chris64233.crewduty.service.QualificationReport;
import com.chris64233.crewduty.web.dto.ComboDetailResponse;
import com.chris64233.crewduty.web.dto.PublishComboRequest;
import com.chris64233.crewduty.web.dto.QualificationResultResponse;
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
 * 值勤组合接口：发布、详情、发布预检、已发布组合重新校验。
 */
@RestController
@RequestMapping("/api/combos")
public class DutyComboController {

    private final CrewDutyService dutyService;

    public DutyComboController(CrewDutyService dutyService) {
        this.dutyService = dutyService;
    }

    /** 发布组合；任一人不合格整组拒绝（422），bizNo 保证幂等。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ComboDetailResponse publish(@Valid @RequestBody PublishComboRequest request) {
        return dutyService.publish(request);
    }

    /** 发布预检：不落库，返回全部资格失败原因。 */
    @PostMapping("/precheck")
    public QualificationResultResponse precheck(@Valid @RequestBody PublishComboRequest request) {
        QualificationReport report = dutyService.precheckPublish(request);
        return new QualificationResultResponse(report.valid(), report.failures());
    }

    @GetMapping("/{bizNo}")
    public ComboDetailResponse detail(@PathVariable String bizNo) {
        return dutyService.getCombo(bizNo);
    }

    /** 查询某已发布组合按成员当前档案重新校验的资格结果与失败原因。 */
    @GetMapping("/{bizNo}/qualification")
    public QualificationResultResponse qualification(@PathVariable String bizNo) {
        QualificationReport report = dutyService.revalidateCombo(bizNo);
        return new QualificationResultResponse(report.valid(), report.failures());
    }
}
