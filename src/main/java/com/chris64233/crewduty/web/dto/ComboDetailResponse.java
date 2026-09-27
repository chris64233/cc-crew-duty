package com.chris64233.crewduty.web.dto;

import com.chris64233.crewduty.domain.DutyCombo;
import com.chris64233.crewduty.domain.DutySegment;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * 值勤组合详情（含成员资格快照与当前版本）。
 */
public record ComboDetailResponse(
        Long id,
        String bizNo,
        String status,
        long version,
        Instant publishedAt,
        Instant lastAdjustedAt,
        List<SegmentView> segments) {

    public static ComboDetailResponse from(DutyCombo combo) {
        return new ComboDetailResponse(
                combo.getId(), combo.getBizNo(), combo.getStatus().name(), combo.getVersion(),
                combo.getPublishedAt(), combo.getLastAdjustedAt(),
                combo.getSegments().stream().map(SegmentView::from).toList());
    }

    public record SegmentView(
            int seqNo,
            String flightNo,
            LocalDateTime departureAt,
            LocalDateTime arrivalAt,
            String aircraftType,
            String requiredPosition,
            String memberEmployeeNo,
            String memberName,
            // 发布/交换时固化的成员资格快照
            String snapshotPosition,
            SortedSet<String> snapshotAircraftTypes,
            LocalDate snapshotQualificationExpiresOn) {

        public static SegmentView from(DutySegment s) {
            return new SegmentView(
                    s.getSeqNo(), s.getFlightNo(), s.getDepartureAt(), s.getArrivalAt(),
                    s.getAircraftType(), s.getRequiredPosition().name(),
                    s.getMember().getEmployeeNo(), s.getMember().getName(),
                    s.getSnapshotPosition().name(),
                    new TreeSet<>(s.getSnapshotAircraftTypes()),
                    s.getSnapshotQualificationExpiresOn());
        }
    }
}
