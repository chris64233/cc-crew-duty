package com.chris64233.crewduty.web.dto;

import com.chris64233.crewduty.domain.DutySegment;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 成员值勤时间线条目（跨全部已发布组合，按出发时间升序）。
 */
public record TimelineEntry(
        String comboBizNo,
        Long comboVersion,
        int segmentSeqNo,
        String flightNo,
        LocalDateTime departureAt,
        LocalDateTime arrivalAt,
        String aircraftType,
        String requiredPosition) {

    public static TimelineEntry from(DutySegment s) {
        return new TimelineEntry(
                s.getCombo().getBizNo(), s.getCombo().getVersion(), s.getSeqNo(),
                s.getFlightNo(), s.getDepartureAt(), s.getArrivalAt(),
                s.getAircraftType(), s.getRequiredPosition().name());
    }

    public record TimelineResponse(String employeeNo, List<TimelineEntry> entries) {
    }
}
