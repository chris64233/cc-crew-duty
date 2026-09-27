package com.chris64233.crewduty.web.dto;

import com.chris64233.crewduty.service.FailureReason;
import java.util.List;

/**
 * 资格预检/失败原因响应。
 */
public record QualificationResultResponse(boolean valid, List<FailureReason> failures) {
}
