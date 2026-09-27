package com.chris64233.crewduty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

import java.time.Instant;

/**
 * 成员交换方案：两个已发布组合之间交换两名成员。
 * 提交时记录双方组合版本，确认时版本必须仍然匹配，否则方案失效。
 */
@Entity
public class SwapProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String proposalNo;

    @Column(nullable = false)
    private Long pairingAId;

    @Column(nullable = false)
    private Long pairingBId;

    @Column(nullable = false)
    private Long memberAId;

    @Column(nullable = false)
    private Long memberBId;

    /** 提交方案时组合 A 的版本。 */
    @Column(nullable = false)
    private long expectedVersionA;

    /** 提交方案时组合 B 的版本。 */
    @Column(nullable = false)
    private long expectedVersionB;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SwapStatus status = SwapStatus.PENDING;

    @Lob
    @Column
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant decidedAt;

    protected SwapProposal() {
    }

    public SwapProposal(String proposalNo, Long pairingAId, Long pairingBId,
                        Long memberAId, Long memberBId,
                        long expectedVersionA, long expectedVersionB) {
        this.proposalNo = proposalNo;
        this.pairingAId = pairingAId;
        this.pairingBId = pairingBId;
        this.memberAId = memberAId;
        this.memberBId = memberBId;
        this.expectedVersionA = expectedVersionA;
        this.expectedVersionB = expectedVersionB;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    /** 持久化分配 ID 后生成业务方案号。 */
    public void assignProposalNo(String proposalNo) {
        this.proposalNo = proposalNo;
    }

    public String getProposalNo() {
        return proposalNo;
    }

    public Long getPairingAId() {
        return pairingAId;
    }

    public Long getPairingBId() {
        return pairingBId;
    }

    public Long getMemberAId() {
        return memberAId;
    }

    public Long getMemberBId() {
        return memberBId;
    }

    public long getExpectedVersionA() {
        return expectedVersionA;
    }

    public long getExpectedVersionB() {
        return expectedVersionB;
    }

    public SwapStatus getStatus() {
        return status;
    }

    public void setStatus(SwapStatus status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }
}
