package com.chris64233.crewduty.web.dto;

import com.chris64233.crewduty.domain.Position;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * 创建/更新机组成员请求。
 */
public record MemberRequest(
        @NotBlank String employeeNo,
        @NotBlank String name,
        @NotNull Position position,
        @NotEmpty List<@NotBlank String> qualifiedAircraftTypes,
        @NotNull LocalDate qualificationExpiresOn) {
}
