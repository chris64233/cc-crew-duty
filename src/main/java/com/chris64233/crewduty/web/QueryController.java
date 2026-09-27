package com.chris64233.crewduty.web;

import com.chris64233.crewduty.service.CrewDutyService;
import com.chris64233.crewduty.web.dto.AuditResponse;
import com.chris64233.crewduty.web.dto.TimelineEntry;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 查询接口：成员值勤时间线、调整审计记录。
 */
@RestController
@RequestMapping("/api")
public class QueryController {

    private final CrewDutyService dutyService;

    public QueryController(CrewDutyService dutyService) {
        this.dutyService = dutyService;
    }

    /** 成员在全部已发布组合中的值勤时间线，按出发时间升序。 */
    @GetMapping("/members/{employeeNo}/timeline")
    public TimelineEntry.TimelineResponse timeline(@PathVariable String employeeNo) {
        return dutyService.timeline(employeeNo);
    }

    /** 全量审计记录（发布/交换方案提交/确认/拒绝），按时间升序。 */
    @GetMapping("/audits")
    public AuditResponse.ListResponse audits() {
        List<AuditResponse> records = dutyService.audits();
        return new AuditResponse.ListResponse(records);
    }
}
