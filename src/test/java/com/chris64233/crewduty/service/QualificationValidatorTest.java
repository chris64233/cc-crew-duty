package com.chris64233.crewduty.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chris64233.crewduty.config.DutyRuleProperties;
import com.chris64233.crewduty.domain.Position;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link QualificationValidator} 规则单元测试，覆盖岗位、机型、资质有效期、
 * 值勤时长上限与最低休息（含跨组合）规则。
 */
class QualificationValidatorTest {

    private static final LocalDate FUTURE = LocalDate.of(2027, 1, 1);
    private static final LocalDate PAST = LocalDate.of(2025, 1, 1);

    private final QualificationValidator validator = new QualificationValidator(
            new DutyRuleProperties(Duration.ofHours(14), Duration.ofHours(10)));

    private AssignmentView seg(int seq, String flight, LocalDateTime dep, LocalDateTime arr,
                               String aircraft, Position required, String member,
                               Position memberPos, Set<String> memberTypes, LocalDate expires,
                               String comboBizNo) {
        return new AssignmentView(seq, flight, dep, arr, aircraft, required, member, memberPos,
                memberTypes, expires, comboBizNo);
    }

    private AssignmentView validSegment(String member, Position pos, int day, String combo) {
        return seg(0, "CA" + day,
                LocalDateTime.of(2026, 10, day, 8, 0),
                LocalDateTime.of(2026, 10, day, 10, 0),
                "A320", pos, member, pos, Set.of("A320", "B737"), FUTURE, combo);
    }

    @Test
    void fullyValidAssignmentPasses() {
        AssignmentView s = validSegment("E001", Position.CAPTAIN, 1, "C-1");
        QualificationReport report = validator.validate(List.of(List.of(s)), List.of());

        assertThat(report.valid()).isTrue();
        assertThat(report.failures()).isEmpty();
    }

    @Test
    void positionMismatchIsRejected() {
        AssignmentView s = seg(0, "CA1",
                LocalDateTime.of(2026, 10, 1, 8, 0),
                LocalDateTime.of(2026, 10, 1, 10, 0),
                "A320", Position.CAPTAIN, "E001", Position.FIRST_OFFICER,
                Set.of("A320"), FUTURE, "C-1");

        QualificationReport report = validator.validate(List.of(List.of(s)), List.of());

        assertThat(report.valid()).isFalse();
        assertThat(report.failures()).extracting(FailureReason::rule)
                .containsExactly(FailureRule.POSITION_MISMATCH);
        assertThat(report.failures().getFirst().memberEmployeeNo()).isEqualTo("E001");
    }

    @Test
    void missingAircraftQualificationIsRejected() {
        AssignmentView s = seg(0, "CA1",
                LocalDateTime.of(2026, 10, 1, 8, 0),
                LocalDateTime.of(2026, 10, 1, 10, 0),
                "A350", Position.CAPTAIN, "E001", Position.CAPTAIN,
                Set.of("A320"), FUTURE, "C-1");

        QualificationReport report = validator.validate(List.of(List.of(s)), List.of());

        assertThat(report.valid()).isFalse();
        assertThat(report.failures()).extracting(FailureReason::rule)
                .containsExactly(FailureRule.AIRCRAFT_NOT_QUALIFIED);
    }

    @Test
    void expiredQualificationIsRejected() {
        AssignmentView s = seg(0, "CA1",
                LocalDateTime.of(2026, 10, 1, 8, 0),
                LocalDateTime.of(2026, 10, 1, 10, 0),
                "A320", Position.CAPTAIN, "E001", Position.CAPTAIN,
                Set.of("A320"), PAST, "C-1");

        QualificationReport report = validator.validate(List.of(List.of(s)), List.of());

        assertThat(report.valid()).isFalse();
        assertThat(report.failures()).extracting(FailureReason::rule)
                .containsExactly(FailureRule.QUALIFICATION_EXPIRED);
    }

    @Test
    void qualificationExpiresOnDepartureDayIsStillValid() {
        LocalDate departureDay = LocalDate.of(2026, 10, 1);
        AssignmentView s = seg(0, "CA1",
                departureDay.atTime(8, 0), departureDay.atTime(10, 0),
                "A320", Position.CAPTAIN, "E001", Position.CAPTAIN,
                Set.of("A320"), departureDay, "C-1");

        assertThat(validator.validate(List.of(List.of(s)), List.of()).valid()).isTrue();
    }

    @Test
    void dutyWindowOverLimitIsRejected() {
        // 08:00 -> 23:30 = 15.5h，超过 14h 上限
        AssignmentView s1 = seg(0, "CA1",
                LocalDateTime.of(2026, 10, 1, 8, 0),
                LocalDateTime.of(2026, 10, 1, 9, 0),
                "A320", Position.CAPTAIN, "E001", Position.CAPTAIN,
                Set.of("A320"), FUTURE, "C-1");
        AssignmentView s2 = seg(1, "CA2",
                LocalDateTime.of(2026, 10, 1, 22, 0),
                LocalDateTime.of(2026, 10, 1, 23, 30),
                "A320", Position.FIRST_OFFICER, "E002", Position.FIRST_OFFICER,
                Set.of("A320"), FUTURE, "C-1");

        QualificationReport report = validator.validate(List.of(List.of(s1, s2)), List.of());

        assertThat(report.valid()).isFalse();
        assertThat(report.failures()).extracting(FailureReason::rule)
                .containsExactly(FailureRule.DUTY_TIME_EXCEEDED);
    }

    @Test
    void insufficientRestInsideSameComboIsRejected() {
        AssignmentView s1 = seg(0, "CA1",
                LocalDateTime.of(2026, 10, 1, 8, 0),
                LocalDateTime.of(2026, 10, 1, 12, 0),
                "A320", Position.CAPTAIN, "E001", Position.CAPTAIN,
                Set.of("A320"), FUTURE, "C-1");
        AssignmentView s2 = seg(1, "CA2",
                LocalDateTime.of(2026, 10, 1, 18, 0),
                LocalDateTime.of(2026, 10, 1, 20, 0),
                "A320", Position.CAPTAIN, "E001", Position.CAPTAIN,
                Set.of("A320"), FUTURE, "C-1");

        QualificationReport report = validator.validate(List.of(List.of(s1, s2)), List.of());

        assertThat(report.failures()).extracting(FailureReason::rule)
                .contains(FailureRule.INSUFFICIENT_REST);
        assertThat(report.valid()).isFalse();
    }

    @Test
    void crossComboRestViolationIsDetected() {
        // 已存在组合中的段：10 月 1 日 20:00 到达
        AssignmentView external = seg(0, "CA-OLD",
                LocalDateTime.of(2026, 10, 1, 16, 0),
                LocalDateTime.of(2026, 10, 1, 20, 0),
                "A320", Position.CAPTAIN, "E001", Position.CAPTAIN,
                Set.of("A320"), FUTURE, "C-OLD");
        // 待发布组合中的段：次日 05:00 出发，只休息 9h
        AssignmentView incoming = seg(0, "CA-NEW",
                LocalDateTime.of(2026, 10, 2, 5, 0),
                LocalDateTime.of(2026, 10, 2, 7, 0),
                "A320", Position.CAPTAIN, "E001", Position.CAPTAIN,
                Set.of("A320"), FUTURE, "C-NEW");

        QualificationReport report = validator.validate(
                List.of(List.of(incoming)), List.of(external));

        assertThat(report.valid()).isFalse();
        assertThat(report.failures()).extracting(FailureReason::rule)
                .containsExactly(FailureRule.INSUFFICIENT_REST);
    }

    @Test
    void restExactlyAtMinimumIsAccepted() {
        AssignmentView external = seg(0, "CA-OLD",
                LocalDateTime.of(2026, 10, 1, 16, 0),
                LocalDateTime.of(2026, 10, 1, 20, 0),
                "A320", Position.CAPTAIN, "E001", Position.CAPTAIN,
                Set.of("A320"), FUTURE, "C-OLD");
        AssignmentView incoming = seg(0, "CA-NEW",
                LocalDateTime.of(2026, 10, 2, 6, 0),
                LocalDateTime.of(2026, 10, 2, 8, 0),
                "A320", Position.CAPTAIN, "E001", Position.CAPTAIN,
                Set.of("A320"), FUTURE, "C-NEW");

        assertThat(validator.validate(List.of(List.of(incoming)), List.of(external)).valid())
                .isTrue();
    }

    @Test
    void overlappingSegmentsAreRejected() {
        AssignmentView s1 = seg(0, "CA1",
                LocalDateTime.of(2026, 10, 1, 8, 0),
                LocalDateTime.of(2026, 10, 1, 12, 0),
                "A320", Position.CAPTAIN, "E001", Position.CAPTAIN,
                Set.of("A320"), FUTURE, "C-1");
        AssignmentView s2 = seg(1, "CA2",
                LocalDateTime.of(2026, 10, 1, 11, 0),
                LocalDateTime.of(2026, 10, 1, 13, 0),
                "A320", Position.FIRST_OFFICER, "E002", Position.FIRST_OFFICER,
                Set.of("A320"), FUTURE, "C-1");

        QualificationReport report = validator.validate(List.of(List.of(s1, s2)), List.of());

        assertThat(report.failures()).extracting(FailureReason::rule)
                .contains(FailureRule.SEGMENT_TIME_INVALID);
    }

    @Test
    void multipleFailuresAreAllCollected() {
        // 岗位不匹配 + 机型缺失 + 资质过期同时存在
        AssignmentView s = seg(0, "CA1",
                LocalDateTime.of(2026, 10, 1, 8, 0),
                LocalDateTime.of(2026, 10, 1, 10, 0),
                "A350", Position.CAPTAIN, "E001", Position.FLIGHT_ATTENDANT,
                Set.of("B737"), PAST, "C-1");

        QualificationReport report = validator.validate(List.of(List.of(s)), List.of());

        assertThat(report.failures()).hasSize(3);
        assertThat(report.failures()).extracting(FailureReason::rule)
                .containsExactlyInAnyOrder(
                        FailureRule.POSITION_MISMATCH,
                        FailureRule.AIRCRAFT_NOT_QUALIFIED,
                        FailureRule.QUALIFICATION_EXPIRED);
    }
}
