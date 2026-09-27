package com.chris64233.crewduty.web.dto;

import com.chris64233.crewduty.domain.Position;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 发布值勤组合请求。{@code segments} 必须按时间排列，每段指定执飞成员工号。
 */
public record PublishComboRequest(
        @NotBlank String bizNo,
        @NotEmpty List<@Valid SegmentRequest> segments) {

    public record SegmentRequest(
            @NotBlank String flightNo,
            @NotNull LocalDateTime departureAt,
            @NotNull LocalDateTime arrivalAt,
            @NotBlank String aircraftType,
            @NotNull Position requiredPosition,
            @NotBlank String memberEmployeeNo) {
    }
}
