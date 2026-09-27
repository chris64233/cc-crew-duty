package com.chris64233.crewduty.service;

import com.chris64233.crewduty.api.Dtos.QualificationFailure;
import com.chris64233.crewduty.domain.CrewMember;
import com.chris64233.crewduty.domain.CrewQualification;
import com.chris64233.crewduty.domain.DutyPairing;
import com.chris64233.crewduty.domain.DutySegment;
import com.chris64233.crewduty.domain.PairingAssignment;
import com.chris64233.crewduty.domain.PairingStatus;
import com.chris64233.crewduty.repo.CrewMemberRepository;
import com.chris64233.crewduty.repo.DutyPairingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 组合资格校验器。发布和交换确认时都对完整组合执行全部规则：
 * 1. 岗位覆盖：每个值勤段的所需岗位都有对应岗位的成员；
 * 2. 机型资质：每位成员对每个值勤段的机型都持有有效期内的资质；
 * 3. 值勤时长：组合总值勤时长不超过上限；
 * 4. 最低休息：成员相邻已发布组合之间的休息间隔不低于下限。
 */
@Component
public class QualificationValidator {

    private final CrewMemberRepository memberRepository;
    private final DutyPairingRepository pairingRepository;
    private final long maxDutyHours;
    private final long minRestHours;

    public QualificationValidator(CrewMemberRepository memberRepository,
                                  DutyPairingRepository pairingRepository,
                                  @Value("${crewduty.max-duty-hours:14}") long maxDutyHours,
                                  @Value("${crewduty.min-rest-hours:10}") long minRestHours) {
        this.memberRepository = memberRepository;
        this.pairingRepository = pairingRepository;
        this.maxDutyHours = maxDutyHours;
        this.minRestHours = minRestHours;
    }

    /**
     * 校验一个完整组合，返回全部失败原因（空列表表示通过）。
     *
     * @param pairing 待校验组合（含值勤段和指派）
     */
    public List<QualificationFailure> validate(DutyPairing pairing) {
        List<QualificationFailure> failures = new ArrayList<>();

        Map<Long, CrewMember> members = loadMembers(pairing, failures);
        validatePositionCoverage(pairing, failures);
        validateAircraftQualification(pairing, members, failures);
        validateDutyDuration(pairing, failures);
        validateRestRequirement(pairing, members, failures);

        return failures;
    }

    private Map<Long, CrewMember> loadMembers(DutyPairing pairing, List<QualificationFailure> failures) {
        Map<Long, CrewMember> members = new HashMap<>();
        for (PairingAssignment assignment : pairing.getAssignments()) {
            Long memberId = assignment.getMemberId();
            if (members.containsKey(memberId)) {
                continue;
            }
            CrewMember member = memberRepository.findById(memberId).orElse(null);
            if (member == null) {
                failures.add(new QualificationFailure("MEMBER_NOT_FOUND",
                        "成员 " + memberId + " 不存在"));
            } else {
                members.put(memberId, member);
                if (member.getPosition() != assignment.getPosition()) {
                    failures.add(new QualificationFailure("POSITION_MISMATCH",
                            "成员 " + member.getEmployeeNo() + " 的岗位 " + member.getPosition()
                                    + " 与指派岗位 " + assignment.getPosition() + " 不一致"));
                }
            }
        }
        return members;
    }

    /** 规则 1：每个值勤段的所需岗位必须至少有一名该岗位的成员。 */
    private void validatePositionCoverage(DutyPairing pairing, List<QualificationFailure> failures) {
        for (DutySegment segment : pairing.getSegments()) {
            boolean covered = pairing.getAssignments().stream()
                    .anyMatch(a -> a.getPosition() == segment.getRequiredPosition());
            if (!covered) {
                failures.add(new QualificationFailure("POSITION_NOT_COVERED",
                        "值勤段 " + segment.getFlightNo() + " 所需岗位 "
                                + segment.getRequiredPosition() + " 无成员覆盖"));
            }
        }
    }

    /** 规则 2：每位成员对组合内每个值勤段的机型都必须持有有效期内资质。 */
    private void validateAircraftQualification(DutyPairing pairing, Map<Long, CrewMember> members,
                                               List<QualificationFailure> failures) {
        for (PairingAssignment assignment : pairing.getAssignments()) {
            CrewMember member = members.get(assignment.getMemberId());
            if (member == null) {
                continue;
            }
            for (DutySegment segment : pairing.getSegments()) {
                boolean qualified = member.getQualifications().stream()
                        .anyMatch(q -> q.covers(segment.getAircraftType(),
                                segment.getDepTime().toLocalDate()));
                if (!qualified) {
                    failures.add(new QualificationFailure("AIRCRAFT_NOT_QUALIFIED",
                            "成员 " + member.getEmployeeNo() + " 对机型 " + segment.getAircraftType()
                                    + " 在 " + segment.getDepTime().toLocalDate() + " 无有效资质（值勤段 "
                                    + segment.getFlightNo() + "）"));
                }
            }
        }
    }

    /** 规则 3：组合总值勤时长（首段起飞到末段到达）不超过上限。 */
    private void validateDutyDuration(DutyPairing pairing, List<QualificationFailure> failures) {
        LocalDateTime start = pairing.dutyStart();
        LocalDateTime end = pairing.dutyEnd();
        if (start == null || end == null) {
            failures.add(new QualificationFailure("NO_SEGMENTS", "组合不包含任何值勤段"));
            return;
        }
        long hours = Duration.between(start, end).toHours();
        if (hours > maxDutyHours) {
            failures.add(new QualificationFailure("DUTY_TIME_EXCEEDED",
                    "值勤时长 " + hours + " 小时超过上限 " + maxDutyHours + " 小时"));
        }
    }

    /** 规则 4：成员在相邻已发布组合之间必须获得最低休息时长。 */
    private void validateRestRequirement(DutyPairing pairing, Map<Long, CrewMember> members,
                                         List<QualificationFailure> failures) {
        LocalDateTime start = pairing.dutyStart();
        LocalDateTime end = pairing.dutyEnd();
        if (start == null || end == null) {
            return;
        }
        List<DutyPairing> published = pairingRepository.findByStatus(PairingStatus.PUBLISHED);
        for (PairingAssignment assignment : pairing.getAssignments()) {
            CrewMember member = members.get(assignment.getMemberId());
            if (member == null) {
                continue;
            }
            for (DutyPairing other : published) {
                if (other.getId().equals(pairing.getId())) {
                    continue;
                }
                boolean assigned = other.getAssignments().stream()
                        .anyMatch(a -> a.getMemberId().equals(assignment.getMemberId()));
                if (!assigned) {
                    continue;
                }
                LocalDateTime otherStart = other.dutyStart();
                LocalDateTime otherEnd = other.dutyEnd();
                if (otherStart == null || otherEnd == null) {
                    continue;
                }
                long restHours = restBetween(start, end, otherStart, otherEnd);
                if (restHours < minRestHours) {
                    failures.add(new QualificationFailure("INSUFFICIENT_REST",
                            "成员 " + member.getEmployeeNo() + " 与已发布组合 " + other.getPairingNo()
                                    + " 之间休息 " + restHours + " 小时，低于最低要求 " + minRestHours + " 小时"));
                }
            }
        }
    }

    /** 两个值勤窗口之间的休息小时数；窗口重叠时为负数。 */
    private long restBetween(LocalDateTime startA, LocalDateTime endA,
                             LocalDateTime startB, LocalDateTime endB) {
        if (!endA.isAfter(startB)) {
            return Duration.between(endA, startB).toHours();
        }
        if (!endB.isAfter(startA)) {
            return Duration.between(endB, startA).toHours();
        }
        return -1;
    }
}
