package com.chris64233.crewduty.service;

import com.chris64233.crewduty.config.DutyRuleProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 资格校验器：对一个或多个“假设值勤组合”执行完整业务规则校验。
 *
 * <p>规则（任一不通过则整组不合格，调用方必须整体拒绝，不允许部分生效）：
 * <ol>
 *   <li>岗位覆盖：每段成员岗位必须等于该段所需岗位；</li>
 *   <li>机型资质：成员可执飞机型必须包含该段机型；</li>
 *   <li>资质有效期：段出发当日不得晚于成员资质有效截止日；</li>
 *   <li>值勤时长上限：单个组合首段出发到末段到达不得超过配置上限；</li>
 *   <li>最低休息：同一成员任意相邻两段值勤（含跨组合的段）之间间隔
 *       不得低于配置的最低休息时间。</li>
 * </ol>
 *
 * 发布时传入 1 个假设组合 + 库中已有段；交换确认时传入交换后的两个假设组合
 * + 两个组合之外的已有段，从而“重新校验双方完整组合”并覆盖跨组合冲突。
 */
@Component
public class QualificationValidator {

    private final DutyRuleProperties rules;

    public QualificationValidator(DutyRuleProperties rules) {
        this.rules = rules;
    }

    /**
     * 执行校验。
     *
     * @param hypotheticalSpans 本次待生效的一个或多个组合，每个组合为按时序排列的段
     * @param externalSegments  不受本次变更影响的其他已发布组合的段（跨组合休息检查用）
     * @return 汇总报告，包含全部失败原因（不是遇错即停）
     */
    public QualificationReport validate(List<List<AssignmentView>> hypotheticalSpans,
                                        List<AssignmentView> externalSegments) {
        List<FailureReason> failures = new ArrayList<>();

        Set<String> hypotheticalComboBizNos = new HashSet<>();
        List<AssignmentView> hypotheticalAll = new ArrayList<>();
        for (List<AssignmentView> span : hypotheticalSpans) {
            validateSpanShape(span, failures);
            for (AssignmentView seg : span) {
                hypotheticalComboBizNos.add(seg.sourceComboBizNo());
                hypotheticalAll.add(seg);
                validateSegment(seg, failures);
            }
            validateDutyWindow(span, failures);
        }

        validateRestAcrossTimeline(hypotheticalAll, externalSegments,
                hypotheticalComboBizNos, failures);

        return new QualificationReport(failures.isEmpty(), failures);
    }

    /** 段时间合法性：到达晚于出发；组合内相邻段不得重叠。 */
    private void validateSpanShape(List<AssignmentView> span, List<FailureReason> failures) {
        List<AssignmentView> ordered = span.stream()
                .sorted(Comparator.comparingInt(AssignmentView::seqNo))
                .toList();
        String comboBizNo = ordered.isEmpty() ? null : ordered.getFirst().sourceComboBizNo();

        if (ordered.isEmpty()) {
            failures.add(FailureReason.combo(FailureRule.SEGMENT_TIME_INVALID,
                    "值勤组合至少需要包含一个值勤段", comboBizNo));
            return;
        }

        for (AssignmentView seg : ordered) {
            if (!seg.arrivalAt().isAfter(seg.departureAt())) {
                failures.add(FailureReason.segment(FailureRule.SEGMENT_TIME_INVALID,
                        "航班 %s 的到达时间必须晚于出发时间".formatted(seg.flightNo()), seg));
            }
        }
        for (int i = 1; i < ordered.size(); i++) {
            AssignmentView prev = ordered.get(i - 1);
            AssignmentView curr = ordered.get(i);
            if (curr.departureAt().isBefore(prev.arrivalAt())) {
                failures.add(FailureReason.segment(FailureRule.SEGMENT_TIME_INVALID,
                        "航班 %s 与前一航班 %s 的时间重叠".formatted(
                                curr.flightNo(), prev.flightNo()), curr));
            }
        }
    }

    /** 单段岗位/机型/有效期三项成员资质校验。 */
    private void validateSegment(AssignmentView seg, List<FailureReason> failures) {
        if (seg.memberPosition() != seg.requiredPosition()) {
            failures.add(FailureReason.segment(FailureRule.POSITION_MISMATCH,
                    "成员 %s 岗位 %s 不满足航班 %s 所需岗位 %s".formatted(
                            seg.memberEmployeeNo(), seg.memberPosition(),
                            seg.flightNo(), seg.requiredPosition()), seg));
        }
        if (!seg.memberAircraftTypes().contains(seg.aircraftType())) {
            failures.add(FailureReason.segment(FailureRule.AIRCRAFT_NOT_QUALIFIED,
                    "成员 %s 不具备机型 %s 的执飞资质（航班 %s）".formatted(
                            seg.memberEmployeeNo(), seg.aircraftType(), seg.flightNo()), seg));
        }
        if (seg.departureAt().toLocalDate().isAfter(seg.qualificationExpiresOn())) {
            failures.add(FailureReason.segment(FailureRule.QUALIFICATION_EXPIRED,
                    "成员 %s 的资质已于 %s 过期，不能执飞出发日 %s 的航班 %s".formatted(
                            seg.memberEmployeeNo(), seg.qualificationExpiresOn(),
                            seg.departureAt().toLocalDate(), seg.flightNo()), seg));
        }
    }

    /** 组合值勤时长上限（首段出发 -> 末段到达）。 */
    private void validateDutyWindow(List<AssignmentView> span, List<FailureReason> failures) {
        if (span.size() < 2) {
            return;
        }
        LocalDateTime start = span.stream().map(AssignmentView::departureAt)
                .min(LocalDateTime::compareTo).orElseThrow();
        LocalDateTime end = span.stream().map(AssignmentView::arrivalAt)
                .max(LocalDateTime::compareTo).orElseThrow();
        Duration duty = Duration.between(start, end);
        if (duty.compareTo(rules.maxDutyHours()) > 0) {
            String comboBizNo = span.getFirst().sourceComboBizNo();
            failures.add(FailureReason.combo(FailureRule.DUTY_TIME_EXCEEDED,
                    "组合值勤时长 %s 超过上限 %s（%s 至 %s）".formatted(
                            formatDuration(duty), formatDuration(rules.maxDutyHours()),
                            start, end), comboBizNo));
        }
    }

    /**
     * 按成员汇总完整时间线（假设段 + 不受影响的已有段），检查相邻段最低休息。
     *
     * <p>只报告“至少一侧属于本次待生效组合”的相邻对：纯外部段之间的冲突在它们
     * 当初发布/交换时已经校验过，避免与本次变更无关的历史数据阻塞当前操作。
     */
    private void validateRestAcrossTimeline(List<AssignmentView> hypotheticalAll,
                                            List<AssignmentView> externalSegments,
                                            Set<String> hypotheticalComboBizNos,
                                            List<FailureReason> failures) {
        Map<String, List<AssignmentView>> byMember = new HashMap<>();
        for (AssignmentView seg : hypotheticalAll) {
            byMember.computeIfAbsent(seg.memberEmployeeNo(), k -> new ArrayList<>()).add(seg);
        }
        for (AssignmentView seg : externalSegments) {
            byMember.computeIfAbsent(seg.memberEmployeeNo(), k -> new ArrayList<>()).add(seg);
        }

        for (List<AssignmentView> timeline : byMember.values()) {
            timeline.sort(Comparator.comparing(AssignmentView::departureAt)
                    .thenComparing(AssignmentView::arrivalAt));
            for (int i = 1; i < timeline.size(); i++) {
                AssignmentView prev = timeline.get(i - 1);
                AssignmentView curr = timeline.get(i);
                boolean involvesHypothetical =
                        hypotheticalComboBizNos.contains(prev.sourceComboBizNo())
                                || hypotheticalComboBizNos.contains(curr.sourceComboBizNo());
                if (!involvesHypothetical) {
                    continue;
                }
                Duration rest = Duration.between(prev.arrivalAt(), curr.departureAt());
                if (rest.compareTo(rules.minRestHours()) < 0) {
                    String msg = rest.isNegative() || rest.isZero()
                            ? "成员 %s 的航班 %s 与航班 %s 值勤时间重叠，未满足最低休息 %s".formatted(
                                    curr.memberEmployeeNo(), prev.flightNo(), curr.flightNo(),
                                    formatDuration(rules.minRestHours()))
                            : "成员 %s 在航班 %s(%s 到达) 与航班 %s(%s 出发) 之间仅休息 %s，低于最低要求 %s"
                                    .formatted(curr.memberEmployeeNo(),
                                            prev.flightNo(), prev.arrivalAt(),
                                            curr.flightNo(), curr.departureAt(),
                                            formatDuration(rest),
                                            formatDuration(rules.minRestHours()));
                    failures.add(FailureReason.rest(msg, prev, curr));
                }
            }
        }
    }

    private static String formatDuration(Duration d) {
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        return minutes == 0 ? "%dh".formatted(hours) : "%dh%dm".formatted(hours, minutes);
    }
}
