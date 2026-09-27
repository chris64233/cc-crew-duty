package com.chris64233.crewduty.service;

import com.chris64233.crewduty.domain.AuditAction;
import com.chris64233.crewduty.domain.AuditRecord;
import com.chris64233.crewduty.domain.DutyCombo;
import com.chris64233.crewduty.domain.DutySegment;
import com.chris64233.crewduty.domain.CrewMember;
import com.chris64233.crewduty.domain.SwapItem;
import com.chris64233.crewduty.domain.SwapProposal;
import com.chris64233.crewduty.repository.AuditRecordRepository;
import com.chris64233.crewduty.repository.DutyComboRepository;
import com.chris64233.crewduty.repository.DutySegmentRepository;
import com.chris64233.crewduty.repository.SwapProposalRepository;
import com.chris64233.crewduty.web.ApiException;
import com.chris64233.crewduty.web.ErrorCode;
import com.chris64233.crewduty.web.dto.ComboDetailResponse;
import com.chris64233.crewduty.web.dto.PublishComboRequest;
import com.chris64233.crewduty.web.dto.SwapProposalRequest;
import com.chris64233.crewduty.web.dto.SwapProposalResponse;
import com.chris64233.crewduty.web.dto.TimelineEntry;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 值勤组合发布、交换、查询的核心领域服务。
 *
 * <p>关键并发保证：
 * <ul>
 *   <li>发布/交换的业务号唯一约束 + 内容指纹实现“同号同内容重放返回原结果、
 *       同号不同内容返回冲突”；</li>
 *   <li>交换确认在一个事务内完成：先锁方案行，再按组合 id 升序加悲观写锁，
 *       校验冻结版本，任一组合版本变化即作废旧方案，然后重新校验双方完整组合，
 *       全部通过才应用成员替换（乐观锁版本随脏检查递增）并提交；</li>
 *   <li>任一成员不合格整体拒绝，不存在缺员或部分生效的组合。</li>
 * </ul>
 */
@Service
public class CrewDutyService {

    private final DutyComboRepository comboRepository;
    private final DutySegmentRepository segmentRepository;
    private final SwapProposalRepository proposalRepository;
    private final AuditRecordRepository auditRepository;
    private final CrewMemberService memberService;
    private final QualificationValidator validator;
    private final ContentFingerprinter fingerprinter;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    public CrewDutyService(DutyComboRepository comboRepository,
                           DutySegmentRepository segmentRepository,
                           SwapProposalRepository proposalRepository,
                           AuditRecordRepository auditRepository,
                           CrewMemberService memberService,
                           QualificationValidator validator,
                           ContentFingerprinter fingerprinter,
                           TransactionTemplate transactionTemplate,
                           EntityManager entityManager) {
        this.comboRepository = comboRepository;
        this.segmentRepository = segmentRepository;
        this.proposalRepository = proposalRepository;
        this.auditRepository = auditRepository;
        this.memberService = memberService;
        this.validator = validator;
        this.fingerprinter = fingerprinter;
        this.transactionTemplate = transactionTemplate;
        this.entityManager = entityManager;
    }

    // ============================== 发布 ==============================

    /**
     * 原子发布组合。不合格时整体拒绝并抛出 {@link ErrorCode#QUALIFICATION_FAILED}，
     * 不产生任何组合数据。
     */
    @Transactional
    public ComboDetailResponse publish(PublishComboRequest request) {
        var existing = comboRepository.findByBizNo(request.bizNo());
        if (existing.isPresent()) {
            replayOrConflict(request, existing.get());
            return ComboDetailResponse.from(existing.get());
        }

        validateSegmentOrder(request);
        Map<String, CrewMember> members = resolveParticipants(request);
        List<AssignmentView> span = buildRequestedSpan(request, members);
        List<AssignmentView> external = toViews(
                segmentRepository.findByMemberEmployeeNoIn(members.keySet()));

        QualificationReport report = validator.validate(List.of(span), external);
        if (!report.valid()) {
            throw new ApiException(ErrorCode.QUALIFICATION_FAILED,
                    "组合发布资格校验失败，整组发布已拒绝", List.copyOf(report.failures()));
        }

        Instant now = Instant.now();
        DutyCombo combo = new DutyCombo(request.bizNo(), fingerprint(request), now);
        for (PublishComboRequest.SegmentRequest sr : request.segments()) {
            combo.addSegment(new DutySegment(
                    combo.getSegments().size(), sr.flightNo().trim(),
                    sr.departureAt(), sr.arrivalAt(),
                    normalizeAircraft(sr.aircraftType()), sr.requiredPosition(),
                    members.get(sr.memberEmployeeNo())));
        }
        DutyCombo saved = comboRepository.save(combo);

        List<Map<String, Object>> auditSegments = saved.getSegments().stream()
                .map(s -> Map.<String, Object>of(
                        "seqNo", s.getSeqNo(),
                        "flightNo", s.getFlightNo(),
                        "aircraftType", s.getAircraftType(),
                        "requiredPosition", s.getRequiredPosition().name(),
                        "memberEmployeeNo", s.getMember().getEmployeeNo(),
                        "snapshotPosition", s.getSnapshotPosition().name(),
                        "snapshotQualificationExpiresOn",
                        String.valueOf(s.getSnapshotQualificationExpiresOn())))
                .toList();
        writeAudit(AuditAction.COMBO_PUBLISHED, saved.getBizNo(),
                String.valueOf(saved.getId()), Map.of("segments", auditSegments));
        return ComboDetailResponse.from(saved);
    }

    /**
     * 发布前预检：不落库，返回完整资格报告（含全部失败原因）。
     */
    @Transactional(readOnly = true)
    public QualificationReport precheckPublish(PublishComboRequest request) {
        validateSegmentOrder(request);
        Map<String, CrewMember> members = resolveParticipants(request);
        List<AssignmentView> span = buildRequestedSpan(request, members);
        List<AssignmentView> external = toViews(
                segmentRepository.findByMemberEmployeeNoIn(members.keySet()));
        return validator.validate(List.of(span), external);
    }

    private void replayOrConflict(PublishComboRequest request, DutyCombo existing) {
        if (fingerprint(request).equals(existing.getContentFingerprint())) {
            // 相同内容重放：直接返回原结果，不重复创建
            return;
        }
        throw new ApiException(ErrorCode.IDEMPOTENT_CONFLICT,
                "业务号 %s 已用于不同内容的组合发布".formatted(request.bizNo()),
                List.of(Map.of("bizNo", request.bizNo(),
                        "existingComboId", existing.getId(),
                        "existingVersion", existing.getVersion())));
    }

    private void validateSegmentOrder(PublishComboRequest request) {
        for (int i = 1; i < request.segments().size(); i++) {
            var prev = request.segments().get(i - 1);
            var curr = request.segments().get(i);
            if (curr.departureAt().isBefore(prev.departureAt())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "值勤段必须按出发时间排列，第 %d 段早于第 %d 段".formatted(i + 1, i),
                        List.of(Map.of("index", i,
                                "previousFlightNo", prev.flightNo(),
                                "flightNo", curr.flightNo())));
            }
        }
    }

    private Map<String, CrewMember> resolveParticipants(PublishComboRequest request) {
        Map<String, CrewMember> members = new LinkedHashMap<>();
        for (PublishComboRequest.SegmentRequest sr : request.segments()) {
            members.putIfAbsent(sr.memberEmployeeNo(), memberService.resolve(sr.memberEmployeeNo()));
        }
        return members;
    }

    private List<AssignmentView> buildRequestedSpan(PublishComboRequest request,
                                                    Map<String, CrewMember> members) {
        List<AssignmentView> span = new ArrayList<>();
        for (int i = 0; i < request.segments().size(); i++) {
            PublishComboRequest.SegmentRequest sr = request.segments().get(i);
            CrewMember m = members.get(sr.memberEmployeeNo());
            span.add(new AssignmentView(i, sr.flightNo().trim(), sr.departureAt(),
                    sr.arrivalAt(), normalizeAircraft(sr.aircraftType()), sr.requiredPosition(),
                    m.getEmployeeNo(), m.getPosition(),
                    Set.copyOf(m.getQualifiedAircraftTypes()),
                    m.getQualificationExpiresOn(), request.bizNo()));
        }
        return span;
    }

    private String fingerprint(PublishComboRequest request) {
        List<Map<String, Object>> canonical = new ArrayList<>();
        for (int i = 0; i < request.segments().size(); i++) {
            var s = request.segments().get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seqNo", i);
            m.put("flightNo", s.flightNo().trim());
            m.put("departureAt", String.valueOf(s.departureAt()));
            m.put("arrivalAt", String.valueOf(s.arrivalAt()));
            m.put("aircraftType", normalizeAircraft(s.aircraftType()));
            m.put("requiredPosition", s.requiredPosition().name());
            m.put("memberEmployeeNo", s.memberEmployeeNo());
            canonical.add(Map.copyOf(m));
        }
        return fingerprinter.sha256(Map.of("segments", canonical));
    }

    // ============================== 查询 ==============================

    @Transactional(readOnly = true)
    public ComboDetailResponse getCombo(String bizNo) {
        return ComboDetailResponse.from(resolveCombo(bizNo));
    }

    /**
     * 对已发布组合按成员当前档案重新校验，返回与该组合相关的资格失败原因
     * （例如成员资质过期、档案变更后出现的不匹配）。
     */
    @Transactional(readOnly = true)
    public QualificationReport revalidateCombo(String bizNo) {
        DutyCombo combo = resolveCombo(bizNo);
        List<AssignmentView> span = toViews(combo.getSegments());
        List<String> memberNos = combo.getSegments().stream()
                .map(s -> s.getMember().getEmployeeNo()).distinct().toList();
        List<AssignmentView> external = toViews(segmentRepository
                .findByMembersOutsideCombos(memberNos, List.of(combo.getId())));
        // 校验器只报告“至少一侧属于待校验组合”的失败，因此报告中的每条都与该组合相关
        return validator.validate(List.of(span), external);
    }

    @Transactional(readOnly = true)
    public TimelineEntry.TimelineResponse timeline(String employeeNo) {
        memberService.resolve(employeeNo); // 成员不存在则 NOT_FOUND
        List<TimelineEntry> entries = segmentRepository
                .findAllByMemberEmployeeNoOrderByTime(employeeNo).stream()
                .map(TimelineEntry::from)
                .toList();
        return new TimelineEntry.TimelineResponse(employeeNo, entries);
    }

    @Transactional(readOnly = true)
    public List<com.chris64233.crewduty.web.dto.AuditResponse> audits() {
        return auditRepository.findAllByOrderByOccurredAtAscIdAsc().stream()
                .map(com.chris64233.crewduty.web.dto.AuditResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SwapProposalResponse getProposal(String bizNo) {
        return SwapProposalResponse.from(proposalRepository.findByBizNo(bizNo)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "交换方案不存在: %s".formatted(bizNo))));
    }

    private DutyCombo resolveCombo(String bizNo) {
        return comboRepository.findByBizNo(bizNo)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "值勤组合不存在: %s".formatted(bizNo)));
    }

    // ============================== 交换方案 ==============================

    /**
     * 提交交换方案：只做结构校验并冻结双方版本，不执行资格校验
     * （资格在确认时按双方完整组合重新校验）。
     */
    @Transactional
    public SwapProposalResponse proposeSwap(SwapProposalRequest request) {
        var existing = proposalRepository.findByBizNo(request.bizNo());
        if (existing.isPresent()) {
            return replayOrConflictSwap(request, existing.get());
        }

        DutyCombo a = resolveCombo(request.comboABizNo());
        DutyCombo b = resolveCombo(request.comboBBizNo());
        if (a.getId().equals(b.getId())) {
            throw new ApiException(ErrorCode.SWAP_INVALID,
                    "交换必须发生在两个不同的已发布组合之间");
        }
        if (request.items().isEmpty()) {
            throw new ApiException(ErrorCode.SWAP_INVALID, "交换方案至少包含一个交换项");
        }

        Map<String, DutyCombo> sideCombos = new HashMap<>();
        sideCombos.put("A", a);
        sideCombos.put("B", b);
        Set<String> seenKeys = new java.util.HashSet<>();
        List<SwapItem> items = new ArrayList<>();
        for (SwapProposalRequest.SwapItemRequest ir : request.items()) {
            String side = normalizeSide(ir.side());
            if (side == null) {
                throw new ApiException(ErrorCode.SWAP_INVALID,
                        "交换项 side 只能是 A 或 B: %s".formatted(ir.side()));
            }
            DutyCombo combo = sideCombos.get(side);
            DutySegment segment = combo.getSegments().stream()
                    .filter(s -> s.getSeqNo() == ir.segmentSeqNo())
                    .findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.SWAP_INVALID,
                            "%s 方组合不存在序号 %d 的值勤段"
                                    .formatted(side, ir.segmentSeqNo())));
            memberService.resolve(ir.targetEmployeeNo()); // 目标成员必须存在
            String key = side + ":" + segment.getId();
            if (!seenKeys.add(key)) {
                throw new ApiException(ErrorCode.SWAP_INVALID,
                        "交换项重复: %s 方段序号 %d".formatted(side, ir.segmentSeqNo()));
            }
            items.add(new SwapItem(side, segment.getId(), ir.targetEmployeeNo()));
        }

        String fp = swapFingerprint(request);
        SwapProposal proposal = new SwapProposal(request.bizNo(), fp, a, b,
                a.getVersion(), b.getVersion(), items, Instant.now());
        SwapProposal saved = proposalRepository.save(proposal);
        entityManager.flush();

        writeAudit(AuditAction.SWAP_PROPOSED, saved.getBizNo(),
                a.getId() + "," + b.getId(),
                Map.of("comboAVersion", a.getVersion(),
                        "comboBVersion", b.getVersion(),
                        "items", request.items().stream()
                                .map(i -> Map.of("side", normalizeSide(i.side()),
                                        "segmentSeqNo", i.segmentSeqNo(),
                                        "targetEmployeeNo", i.targetEmployeeNo()))
                                .toList()));
        return SwapProposalResponse.from(saved);
    }

    private SwapProposalResponse replayOrConflictSwap(SwapProposalRequest request,
                                                      SwapProposal existing) {
        if (swapFingerprint(request).equals(existing.getContentFingerprint())) {
            return SwapProposalResponse.from(existing);
        }
        throw new ApiException(ErrorCode.IDEMPOTENT_CONFLICT,
                "交换业务号 %s 已用于不同内容的交换方案".formatted(request.bizNo()),
                List.of(Map.of("bizNo", request.bizNo(),
                        "existingProposalId", existing.getId(),
                        "existingStatus", existing.getStatus().name())));
    }

    /**
     * 确认交换：重新校验双方完整组合，一次事务内完成。
     *
     * <p>使用 {@link TransactionTemplate} 编程式事务：方案被作废（REJECTED）时
     * 状态与审计必须随事务提交保留，随后再向调用方抛出错误。
     */
    public SwapProposalResponse confirmSwap(String bizNo) {
        ConfirmOutcome outcome = transactionTemplate.execute(status -> performConfirm(bizNo));
        if (outcome instanceof ConfirmOutcome.Success success) {
            return success.response();
        }
        ConfirmOutcome.Rejected rejected = (ConfirmOutcome.Rejected) outcome;
        throw new ApiException(rejected.errorCode(), rejected.message(), rejected.details());
    }

    private ConfirmOutcome performConfirm(String bizNo) {
        SwapProposal proposal = proposalRepository.findByBizNoForUpdate(bizNo)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "交换方案不存在: %s".formatted(bizNo)));

        if (proposal.getStatus() == com.chris64233.crewduty.domain.SwapStatus.CONFIRMED) {
            return new ConfirmOutcome.Success(SwapProposalResponse.from(proposal));
        }
        if (proposal.getStatus() == com.chris64233.crewduty.domain.SwapStatus.REJECTED) {
            return new ConfirmOutcome.Rejected(ErrorCode.SWAP_REJECTED,
                    "交换方案 %s 已作废，不能再次确认".formatted(bizNo), List.of());
        }

        DutyCombo a = proposal.getComboA();
        DutyCombo b = proposal.getComboB();
        // 按 id 升序加锁，避免与其它并发交换形成死锁
        List<DutyCombo> locked = Stream.of(a, b)
                .sorted(Comparator.comparing(DutyCombo::getId))
                .map(c -> comboRepository.findByIdForUpdate(c.getId()).orElseThrow())
                .toList();
        DutyCombo lockedA = locked.stream().filter(c -> c.getId().equals(a.getId())).findFirst().orElseThrow();
        DutyCombo lockedB = locked.stream().filter(c -> c.getId().equals(b.getId())).findFirst().orElseThrow();

        if (lockedA.getVersion() != proposal.getComboAVersion()
                || lockedB.getVersion() != proposal.getComboBVersion()) {
            return reject(proposal, ErrorCode.CONCURRENT_MODIFICATION,
                    "交换方案基于的组合版本已变化（发生并发交换或排班变更），旧方案不得生效",
                    Map.of("expectedComboAVersion", proposal.getComboAVersion(),
                            "currentComboAVersion", lockedA.getVersion(),
                            "expectedComboBVersion", proposal.getComboBVersion(),
                            "currentComboBVersion", lockedB.getVersion()));
        }

        // 构造交换后的双方假设组合
        Map<Long, String> targetsBySegmentId = proposal.getItems().stream()
                .collect(Collectors.toMap(SwapItem::getSegmentId, SwapItem::getTargetEmployeeNo,
                        (x, y) -> x, LinkedHashMap::new));
        List<AssignmentView> spanA = buildHypotheticalSpan(lockedA, targetsBySegmentId);
        List<AssignmentView> spanB = buildHypotheticalSpan(lockedB, targetsBySegmentId);

        Set<String> participants = new java.util.LinkedHashSet<>();
        spanA.forEach(v -> participants.add(v.memberEmployeeNo()));
        spanB.forEach(v -> participants.add(v.memberEmployeeNo()));
        List<AssignmentView> external = toViews(segmentRepository.findByMembersOutsideCombos(
                participants, List.of(lockedA.getId(), lockedB.getId())));

        QualificationReport report = validator.validate(List.of(spanA, spanB), external);
        if (!report.valid()) {
            return reject(proposal, ErrorCode.QUALIFICATION_FAILED,
                    "交换后资格重新校验失败，交换已整体拒绝", List.copyOf(report.failures()));
        }

        Instant confirmedAt = Instant.now();
        // 应用成员替换并刷新资格快照；显式 touch 组合行以驱动 @Version 递增
        List<Map<String, Object>> changeLog = new ArrayList<>();
        for (SwapItem item : proposal.getItems()) {
            DutyCombo owner = item.getSide().equals("A") ? lockedA : lockedB;
            DutySegment segment = owner.getSegments().stream()
                    .filter(s -> s.getId().equals(item.getSegmentId()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.SWAP_INVALID,
                            "交换项引用的段已不存在: segmentId=%d".formatted(item.getSegmentId())));
            String fromEmployeeNo = segment.getMember().getEmployeeNo();
            CrewMember target = memberService.resolve(item.getTargetEmployeeNo());
            segment.assignMember(target);
            owner.touch(confirmedAt);
            changeLog.add(Map.of("side", item.getSide(),
                    "segmentId", segment.getId(),
                    "flightNo", segment.getFlightNo(),
                    "fromEmployeeNo", fromEmployeeNo,
                    "toEmployeeNo", target.getEmployeeNo()));
        }

        entityManager.flush(); // 触发双方组合版本递增
        proposal.markConfirmed(lockedA.getVersion(), lockedB.getVersion(), confirmedAt);

        writeAudit(AuditAction.SWAP_CONFIRMED, proposal.getBizNo(),
                lockedA.getId() + "," + lockedB.getId(),
                Map.of("changes", changeLog,
                        "resultingComboAVersion", lockedA.getVersion(),
                        "resultingComboBVersion", lockedB.getVersion()));
        return new ConfirmOutcome.Success(SwapProposalResponse.from(proposal));
    }

    private ConfirmOutcome.Rejected reject(SwapProposal proposal, ErrorCode code,
                                           String message, Object detail) {
        proposal.markRejected();
        writeAudit(AuditAction.SWAP_REJECTED, proposal.getBizNo(),
                proposal.getComboA().getId() + "," + proposal.getComboB().getId(),
                Map.of("reason", code.name(), "message", message, "detail", detail));
        @SuppressWarnings("unchecked")
        List<Object> details = detail instanceof List<?> list
                ? (List<Object>) list
                : List.of(detail);
        return new ConfirmOutcome.Rejected(code, message, details);
    }

    private List<AssignmentView> buildHypotheticalSpan(DutyCombo combo,
                                                       Map<Long, String> targetsBySegmentId) {
        List<AssignmentView> span = new ArrayList<>();
        for (DutySegment s : combo.getSegments()) {
            String targetEmployeeNo = targetsBySegmentId.get(s.getId());
            CrewMember effective = targetEmployeeNo == null
                    ? s.getMember()
                    : memberService.resolve(targetEmployeeNo);
            span.add(toView(s, effective));
        }
        return span;
    }

    private String swapFingerprint(SwapProposalRequest request) {
        List<Map<String, Object>> items = request.items().stream()
                .map(i -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("side", normalizeSide(i.side()));
                    m.put("segmentSeqNo", i.segmentSeqNo());
                    m.put("targetEmployeeNo", i.targetEmployeeNo());
                    return Map.copyOf(m);
                })
                .sorted(Comparator.comparing(m -> m.get("side") + ":" + m.get("segmentSeqNo")))
                .toList();
        return fingerprinter.sha256(Map.of(
                "comboABizNo", request.comboABizNo(),
                "comboBBizNo", request.comboBBizNo(),
                "items", items));
    }

    private static String normalizeSide(String side) {
        if (side == null) {
            return null;
        }
        String normalized = side.trim().toUpperCase(Locale.ROOT);
        return normalized.equals("A") || normalized.equals("B") ? normalized : null;
    }

    // ============================== 视图与审计 ==============================

    private AssignmentView toView(DutySegment s, CrewMember effectiveMember) {
        return new AssignmentView(
                s.getSeqNo(), s.getFlightNo(), s.getDepartureAt(), s.getArrivalAt(),
                s.getAircraftType(), s.getRequiredPosition(),
                effectiveMember.getEmployeeNo(), effectiveMember.getPosition(),
                Set.copyOf(effectiveMember.getQualifiedAircraftTypes()),
                effectiveMember.getQualificationExpiresOn(),
                s.getCombo().getBizNo());
    }

    /** 用成员当前档案构造视图（跨组合休息检查、已发布组合重新校验）。 */
    private List<AssignmentView> toViews(List<DutySegment> segments) {
        return segments.stream().map(s -> toView(s, s.getMember())).toList();
    }

    private static String normalizeAircraft(String type) {
        return type.trim().toUpperCase(Locale.ROOT);
    }

    private void writeAudit(AuditAction action, String bizNo, String comboIds, Object detail) {
        auditRepository.save(new AuditRecord(action, bizNo, comboIds,
                Instant.now(), fingerprinter.toJson(detail)));
    }

    /** 交换确认的事务内部结果：成功返回响应，失败携带错误（作废状态已提交）。 */
    private sealed interface ConfirmOutcome {
        record Success(SwapProposalResponse response) implements ConfirmOutcome {
        }

        record Rejected(ErrorCode errorCode, String message, List<Object> details)
                implements ConfirmOutcome {
        }
    }
}
