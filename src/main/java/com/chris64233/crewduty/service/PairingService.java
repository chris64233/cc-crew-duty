package com.chris64233.crewduty.service;

import com.chris64233.crewduty.api.BusinessException;
import com.chris64233.crewduty.api.Dtos;
import com.chris64233.crewduty.api.Dtos.AssignmentDto;
import com.chris64233.crewduty.api.Dtos.AssignmentView;
import com.chris64233.crewduty.api.Dtos.PairingResponse;
import com.chris64233.crewduty.api.Dtos.QualificationFailure;
import com.chris64233.crewduty.api.Dtos.SegmentDto;
import com.chris64233.crewduty.api.Dtos.TimelineEntry;
import com.chris64233.crewduty.domain.AuditRecord;
import com.chris64233.crewduty.domain.CrewMember;
import com.chris64233.crewduty.domain.CrewQualification;
import com.chris64233.crewduty.domain.DutyPairing;
import com.chris64233.crewduty.domain.DutySegment;
import com.chris64233.crewduty.domain.PairingAssignment;
import com.chris64233.crewduty.domain.PairingStatus;
import com.chris64233.crewduty.repo.AuditRecordRepository;
import com.chris64233.crewduty.repo.CrewMemberRepository;
import com.chris64233.crewduty.repo.DutyPairingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 值勤组合服务：创建、发布（资格校验 + 原子生效 + 幂等）、详情、失败原因与成员时间线。
 */
@Service
public class PairingService {

    private final DutyPairingRepository pairingRepository;
    private final CrewMemberRepository memberRepository;
    private final AuditRecordRepository auditRepository;
    private final QualificationValidator validator;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public PairingService(DutyPairingRepository pairingRepository,
                          CrewMemberRepository memberRepository,
                          AuditRecordRepository auditRepository,
                          QualificationValidator validator,
                          IdempotencyService idempotencyService,
                          ObjectMapper objectMapper) {
        this.pairingRepository = pairingRepository;
        this.memberRepository = memberRepository;
        this.auditRepository = auditRepository;
        this.validator = validator;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PairingResponse createPairing(Dtos.CreatePairingRequest request) {
        validateStructure(request);
        if (pairingRepository.existsByPairingNo(request.pairingNo())) {
            throw BusinessException.conflict("PAIRING_NO_DUPLICATED",
                    "组合业务号 " + request.pairingNo() + " 已存在");
        }
        DutyPairing pairing = new DutyPairing(request.pairingNo());
        request.segments().stream()
                .sorted(Comparator.comparingInt(Dtos.SegmentDto::sequenceNo))
                .forEach(s -> pairing.addSegment(new DutySegment(
                        s.sequenceNo(), s.flightNo(), s.aircraftType(),
                        s.requiredPosition(), s.depTime(), s.arrTime())));
        request.assignments().forEach(a ->
                pairing.addAssignment(new PairingAssignment(a.memberId(), a.position())));
        try {
            pairingRepository.save(pairing);
            pairingRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.conflict("PAIRING_NO_DUPLICATED",
                    "组合业务号 " + request.pairingNo() + " 已存在");
        }
        return toResponse(pairing);
    }

    private void validateStructure(Dtos.CreatePairingRequest request) {
        for (var s : request.segments()) {
            if (!s.arrTime().isAfter(s.depTime())) {
                throw BusinessException.unprocessable("INVALID_SEGMENT_TIME",
                        "值勤段 " + s.flightNo() + " 的到达时间必须晚于起飞时间", List.of());
            }
        }
        long distinctMembers = request.assignments().stream()
                .map(Dtos.AssignmentDto::memberId).distinct().count();
        if (distinctMembers != request.assignments().size()) {
            throw BusinessException.unprocessable("DUPLICATED_ASSIGNMENT",
                    "同一成员在一个组合中只能被指派一次", List.of());
        }
    }

    /**
     * 发布组合：幂等、资格校验全部通过后一次性生效；任何成员不合格则整组拒绝。
     * 业务异常不回滚事务，使失败幂等记录和拒绝审计得以保留。
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public PairingResponse publish(Long pairingId, String idemKey) {
        DutyPairing pairing = loadWithDetails(pairingId);
        String fingerprint = "PUBLISH:" + pairingId;
        return idempotencyService.execute(
                idemKey, "PUBLISH", fingerprint,
                () -> doPublish(pairing), PairingResponse.class);
    }

    private PairingResponse doPublish(DutyPairing pairing) {
        if (pairing.getStatus() == PairingStatus.PUBLISHED) {
            throw BusinessException.conflict("PAIRING_ALREADY_PUBLISHED",
                    "组合 " + pairing.getPairingNo() + " 已发布");
        }
        List<QualificationFailure> failures = validator.validate(pairing);
        if (!failures.isEmpty()) {
            audit("PUBLISH_REJECTED", pairing, failures.toString());
            throw BusinessException.unprocessable("QUALIFICATION_FAILED",
                    "组合 " + pairing.getPairingNo() + " 资格校验未通过，整组拒绝发布",
                    failures.stream().map(f -> f.rule() + ": " + f.message()).toList());
        }
        applySnapshots(pairing);
        pairing.setStatus(PairingStatus.PUBLISHED);
        pairing.setPublishedAt(Instant.now());
        audit("PUBLISH", pairing, "组合发布成功，成员 "
                + pairing.getAssignments().stream().map(a -> a.getMemberId().toString())
                .toList());
        // 刷新使乐观锁版本在响应中体现为发布后的新版本
        pairingRepository.flush();
        return toResponse(pairing);
    }

    /** 为每条指派写入成员岗位和机型资质的发布时快照。 */
    void applySnapshots(DutyPairing pairing) {
        Map<Long, CrewMember> members = new HashMap<>();
        for (PairingAssignment assignment : pairing.getAssignments()) {
            CrewMember member = members.computeIfAbsent(assignment.getMemberId(),
                    id -> memberRepository.findById(id)
                            .orElseThrow(() -> BusinessException.notFound("成员 " + id)));
            assignment.setQualificationSnapshot(buildSnapshot(member));
        }
    }

    private String buildSnapshot(CrewMember member) {
        try {
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("employeeNo", member.getEmployeeNo());
            snapshot.put("position", member.getPosition());
            snapshot.put("qualifications", member.getQualifications().stream()
                    .map(this::qualificationView).toList());
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("资格快照生成失败", e);
        }
    }

    private Map<String, Object> qualificationView(CrewQualification q) {
        Map<String, Object> view = new HashMap<>();
        view.put("aircraftType", q.getAircraftType());
        view.put("validFrom", q.getValidFrom().toString());
        view.put("validTo", q.getValidTo().toString());
        return view;
    }

    @Transactional(readOnly = true)
    public PairingResponse getPairing(Long pairingId) {
        return toResponse(loadWithDetails(pairingId));
    }

    /** 查询组合当前的资格失败原因（不改变状态）。 */
    @Transactional(readOnly = true)
    public List<QualificationFailure> getFailures(Long pairingId) {
        return validator.validate(loadWithDetails(pairingId));
    }

    /** 成员值勤时间线：该成员参与的全部组合按值勤开始时间排列。 */
    @Transactional(readOnly = true)
    public List<TimelineEntry> getMemberTimeline(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw BusinessException.notFound("成员 " + memberId);
        }
        return pairingRepository.findAll().stream()
                .filter(p -> p.getAssignments().stream()
                        .anyMatch(a -> a.getMemberId().equals(memberId)))
                .map(p -> new TimelineEntry(
                        p.getId(), p.getPairingNo(), p.getStatus().name(),
                        p.getAssignments().stream()
                                .filter(a -> a.getMemberId().equals(memberId))
                                .findFirst().map(PairingAssignment::getPosition).orElse(null),
                        p.dutyStart(), p.dutyEnd(),
                        p.getSegments().stream().map(this::toSegmentDto).toList()))
                .sorted(Comparator.comparing(TimelineEntry::dutyStart))
                .toList();
    }

    DutyPairing loadWithDetails(Long pairingId) {
        return pairingRepository.findWithSegmentsAndAssignmentsById(pairingId)
                .orElseThrow(() -> BusinessException.notFound("值勤组合 " + pairingId));
    }

    void audit(String action, DutyPairing pairing, String detail) {
        auditRepository.save(new AuditRecord(action, pairing.getId(), detail));
    }

    PairingResponse toResponse(DutyPairing pairing) {
        Map<Long, CrewMember> members = new HashMap<>();
        for (PairingAssignment a : pairing.getAssignments()) {
            members.computeIfAbsent(a.getMemberId(), id ->
                    memberRepository.findById(id).orElse(null));
        }
        List<AssignmentView> assignments = pairing.getAssignments().stream()
                .map(a -> {
                    CrewMember m = members.get(a.getMemberId());
                    return new AssignmentView(
                            a.getMemberId(),
                            m == null ? null : m.getEmployeeNo(),
                            m == null ? null : m.getName(),
                            a.getPosition(),
                            a.getQualificationSnapshot());
                })
                .toList();
        return new PairingResponse(
                pairing.getId(),
                pairing.getPairingNo(),
                pairing.getStatus().name(),
                pairing.getVersion(),
                pairing.getSegments().stream().map(this::toSegmentDto).toList(),
                assignments,
                pairing.getPublishedAt() == null ? null : pairing.getPublishedAt().toString());
    }

    private SegmentDto toSegmentDto(DutySegment s) {
        return new SegmentDto(s.getSequenceNo(), s.getFlightNo(), s.getAircraftType(),
                s.getRequiredPosition(), s.getDepTime(), s.getArrTime());
    }
}
