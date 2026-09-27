package com.chris64233.crewduty.service;

import com.chris64233.crewduty.api.BusinessException;
import com.chris64233.crewduty.api.Dtos.CreateSwapRequest;
import com.chris64233.crewduty.api.Dtos.QualificationFailure;
import com.chris64233.crewduty.api.Dtos.SwapResponse;
import com.chris64233.crewduty.domain.AuditRecord;
import com.chris64233.crewduty.domain.DutyPairing;
import com.chris64233.crewduty.domain.PairingAssignment;
import com.chris64233.crewduty.domain.PairingStatus;
import com.chris64233.crewduty.domain.SwapProposal;
import com.chris64233.crewduty.domain.SwapStatus;
import com.chris64233.crewduty.repo.AuditRecordRepository;
import com.chris64233.crewduty.repo.DutyPairingRepository;
import com.chris64233.crewduty.repo.SwapProposalRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 成员交换服务：提交方案、确认方案。
 * 确认时重新校验双方完整组合，在一次事务中完成；组合版本变化则方案失效。
 */
@Service
public class SwapService {

    private final SwapProposalRepository proposalRepository;
    private final DutyPairingRepository pairingRepository;
    private final AuditRecordRepository auditRepository;
    private final PairingService pairingService;
    private final QualificationValidator validator;
    private final IdempotencyService idempotencyService;

    @PersistenceContext
    private EntityManager entityManager;

    public SwapService(SwapProposalRepository proposalRepository,
                       DutyPairingRepository pairingRepository,
                       AuditRecordRepository auditRepository,
                       PairingService pairingService,
                       QualificationValidator validator,
                       IdempotencyService idempotencyService) {
        this.proposalRepository = proposalRepository;
        this.pairingRepository = pairingRepository;
        this.auditRepository = auditRepository;
        this.pairingService = pairingService;
        this.validator = validator;
        this.idempotencyService = idempotencyService;
    }

    /**
     * 提交交换方案：双方组合必须均已发布，且两名成员当前分别属于两个组合。
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public SwapResponse submit(CreateSwapRequest request) {
        String fingerprint = "SWAP_SUBMIT:" + request.pairingAId() + ":" + request.pairingBId()
                + ":" + request.memberAId() + ":" + request.memberBId();
        return idempotencyService.execute(
                request.idemKey(), "SWAP_SUBMIT", fingerprint,
                () -> doSubmit(request), SwapResponse.class);
    }

    private SwapResponse doSubmit(CreateSwapRequest request) {
        if (request.pairingAId().equals(request.pairingBId())) {
            throw BusinessException.unprocessable("SAME_PAIRING",
                    "交换必须发生在两个不同组合之间", List.of());
        }
        DutyPairing pairingA = pairingService.loadWithDetails(request.pairingAId());
        DutyPairing pairingB = pairingService.loadWithDetails(request.pairingBId());
        if (pairingA.getStatus() != PairingStatus.PUBLISHED
                || pairingB.getStatus() != PairingStatus.PUBLISHED) {
            throw BusinessException.unprocessable("PAIRING_NOT_PUBLISHED",
                    "交换双方组合都必须是已发布状态", List.of());
        }
        if (findAssignment(pairingA, request.memberAId()) == null) {
            throw BusinessException.unprocessable("MEMBER_NOT_IN_PAIRING",
                    "成员 " + request.memberAId() + " 不在组合 " + pairingA.getPairingNo() + " 中",
                    List.of());
        }
        if (findAssignment(pairingB, request.memberBId()) == null) {
            throw BusinessException.unprocessable("MEMBER_NOT_IN_PAIRING",
                    "成员 " + request.memberBId() + " 不在组合 " + pairingB.getPairingNo() + " 中",
                    List.of());
        }
        if (request.memberAId().equals(request.memberBId())) {
            throw BusinessException.unprocessable("SAME_MEMBER", "不能交换同一名成员", List.of());
        }
        SwapProposal proposal = new SwapProposal(
                null, pairingA.getId(), pairingB.getId(),
                request.memberAId(), request.memberBId(),
                pairingA.getVersion(), pairingB.getVersion());
        proposalRepository.save(proposal);
        proposalRepository.flush();
        proposal.assignProposalNo("SWAP-" + String.format("%08d", proposal.getId()));
        auditRepository.save(new AuditRecord("SWAP_SUBMITTED", pairingA.getId(),
                "交换方案 " + proposal.getProposalNo() + " 已提交，等待确认"));
        auditRepository.save(new AuditRecord("SWAP_SUBMITTED", pairingB.getId(),
                "交换方案 " + proposal.getProposalNo() + " 已提交，等待确认"));
        return toResponse(proposal);
    }

    /**
     * 确认交换：版本一致 → 重新校验双方组合 → 一次事务完成成员对调与快照更新。
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public SwapResponse confirm(Long proposalId, String idemKey) {
        SwapProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> BusinessException.notFound("交换方案 " + proposalId));
        return idempotencyService.execute(
                idemKey, "SWAP_CONFIRM", "SWAP_CONFIRM:" + proposalId,
                () -> doConfirm(proposal), SwapResponse.class);
    }

    private SwapResponse doConfirm(SwapProposal proposal) {
        if (proposal.getStatus() != SwapStatus.PENDING) {
            throw BusinessException.conflict("SWAP_NOT_PENDING",
                    "交换方案当前状态为 " + proposal.getStatus() + "，不能确认");
        }
        DutyPairing pairingA = pairingService.loadWithDetails(proposal.getPairingAId());
        DutyPairing pairingB = pairingService.loadWithDetails(proposal.getPairingBId());

        if (pairingA.getVersion() != proposal.getExpectedVersionA()
                || pairingB.getVersion() != proposal.getExpectedVersionB()) {
            proposal.setStatus(SwapStatus.STALE);
            proposal.setFailureReason("组合版本已变化：A=" + pairingA.getVersion()
                    + "/" + proposal.getExpectedVersionA() + "，B=" + pairingB.getVersion()
                    + "/" + proposal.getExpectedVersionB());
            proposal.setDecidedAt(Instant.now());
            auditStale(pairingA, proposal);
            auditStale(pairingB, proposal);
            throw BusinessException.conflict("SWAP_STALE",
                    "组合版本已变化，旧交换方案不得生效");
        }

        PairingAssignment assignmentA = findAssignment(pairingA, proposal.getMemberAId());
        PairingAssignment assignmentB = findAssignment(pairingB, proposal.getMemberBId());
        if (assignmentA == null || assignmentB == null) {
            markRejected(proposal, List.of(new QualificationFailure("MEMBER_NOT_IN_PAIRING",
                    "方案涉及的成员已不在原组合中")));
            throw BusinessException.conflict("SWAP_STALE",
                    "方案涉及的成员已不在原组合中，旧交换方案不得生效");
        }

        // 预演交换并完整重新校验双方组合，任一方不合格则整笔交换拒绝。
        assignmentA.setMemberId(proposal.getMemberBId());
        assignmentB.setMemberId(proposal.getMemberAId());
        List<QualificationFailure> failuresA = validator.validate(pairingA);
        List<QualificationFailure> failuresB = validator.validate(pairingB);
        if (!failuresA.isEmpty() || !failuresB.isEmpty()) {
            // 还原内存中的预演变更，组合本身不发生任何修改。
            assignmentA.setMemberId(proposal.getMemberAId());
            assignmentB.setMemberId(proposal.getMemberBId());
            List<String> all = new java.util.ArrayList<>(
                    failuresA.stream().map(f -> "[" + pairingA.getPairingNo() + "] " + f.rule()
                            + ": " + f.message()).toList());
            failuresB.forEach(f -> all.add("[" + pairingB.getPairingNo() + "] " + f.rule()
                    + ": " + f.message()));
            markRejected(proposal, all.stream()
                    .map(msg -> new QualificationFailure("SWAP_QUALIFICATION_FAILED", msg))
                    .toList());
            pairingService.audit("SWAP_REJECTED", pairingA,
                    "交换方案 " + proposal.getProposalNo() + " 确认时校验未通过：" + all);
            pairingService.audit("SWAP_REJECTED", pairingB,
                    "交换方案 " + proposal.getProposalNo() + " 确认时校验未通过：" + all);
            throw BusinessException.unprocessable("SWAP_QUALIFICATION_FAILED",
                    "交换后资格校验未通过，双方组合均不变更", all);
        }

        pairingService.applySnapshots(pairingA);
        pairingService.applySnapshots(pairingB);
        proposal.setStatus(SwapStatus.CONFIRMED);
        proposal.setDecidedAt(Instant.now());
        pairingService.audit("SWAP_CONFIRMED", pairingA,
                "交换方案 " + proposal.getProposalNo() + "：成员 " + proposal.getMemberBId()
                        + " 进入本组合，成员 " + proposal.getMemberAId() + " 离开");
        pairingService.audit("SWAP_CONFIRMED", pairingB,
                "交换方案 " + proposal.getProposalNo() + "：成员 " + proposal.getMemberAId()
                        + " 进入本组合，成员 " + proposal.getMemberBId() + " 离开");
        // 强制递增两个组合的乐观锁版本（成员对调发生在子表，父实体本身不变），
        // 使并发的旧方案/旧排班读取失效；若数据库版本已被并发事务修改，此处抛乐观锁异常。
        entityManager.lock(pairingA, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        entityManager.lock(pairingB, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        pairingRepository.saveAndFlush(pairingA);
        pairingRepository.saveAndFlush(pairingB);
        return toResponse(proposal);
    }

    private void markRejected(SwapProposal proposal, List<QualificationFailure> failures) {
        proposal.setStatus(SwapStatus.REJECTED);
        proposal.setFailureReason(failures.stream()
                .map(f -> f.rule() + ": " + f.message())
                .reduce((a, b) -> a + "; " + b).orElse("资格校验未通过"));
        proposal.setDecidedAt(Instant.now());
    }

    private void auditStale(DutyPairing pairing, SwapProposal proposal) {
        auditRepository.save(new AuditRecord("SWAP_STALE", pairing.getId(),
                "交换方案 " + proposal.getProposalNo() + " 因组合版本变化失效"));
    }

    private PairingAssignment findAssignment(DutyPairing pairing, Long memberId) {
        return pairing.getAssignments().stream()
                .filter(a -> a.getMemberId().equals(memberId))
                .findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public SwapResponse getProposal(Long proposalId) {
        return proposalRepository.findById(proposalId)
                .map(this::toResponse)
                .orElseThrow(() -> BusinessException.notFound("交换方案 " + proposalId));
    }

    private SwapResponse toResponse(SwapProposal p) {
        return new SwapResponse(
                p.getId(),
                p.getProposalNo(),
                p.getStatus().name(),
                p.getPairingAId(),
                p.getPairingBId(),
                p.getMemberAId(),
                p.getMemberBId(),
                p.getExpectedVersionA(),
                p.getExpectedVersionB(),
                p.getFailureReason(),
                p.getCreatedAt() == null ? null : p.getCreatedAt().toString(),
                p.getDecidedAt() == null ? null : p.getDecidedAt().toString());
    }
}
