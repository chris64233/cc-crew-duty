package com.chris64233.crewduty.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 两个已发布组合之间的成员交换方案。
 *
 * <p>提交时冻结双方组合版本号；确认时若任一组合当前版本与冻结版本不一致，
 * 说明期间发生过并发交换或排班变更，方案作废不得生效。确认业务号同样保证幂等。
 */
@Entity
@Table(name = "swap_proposal")
public class SwapProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 交换确认业务号（首次提交时即要求提供，确认动作幂等） */
    @Column(nullable = false, unique = true)
    private String bizNo;

    /** 交换内容指纹（双方组合 + 交换项集合），用于识别冲突重放 */
    @Column(nullable = false)
    private String contentFingerprint;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SwapStatus status = SwapStatus.PROPOSED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "combo_a_id", nullable = false)
    private DutyCombo comboA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "combo_b_id", nullable = false)
    private DutyCombo comboB;

    /** 提交方案时冻结的双方组合版本 */
    @Column(nullable = false)
    private long comboAVersion;

    @Column(nullable = false)
    private long comboBVersion;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "swap_item", joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position")
    private List<SwapItem> items = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant proposedAt;

    private Instant confirmedAt;

    /** 确认完成后双方组合的新版本，用于幂等重放返回原结果 */
    private Long resultingComboAVersion;

    private Long resultingComboBVersion;

    protected SwapProposal() {
    }

    public SwapProposal(String bizNo, String contentFingerprint, DutyCombo comboA,
                        DutyCombo comboB, long comboAVersion, long comboBVersion,
                        List<SwapItem> items, Instant proposedAt) {
        this.bizNo = bizNo;
        this.contentFingerprint = contentFingerprint;
        this.comboA = comboA;
        this.comboB = comboB;
        this.comboAVersion = comboAVersion;
        this.comboBVersion = comboBVersion;
        this.items = new ArrayList<>(items);
        this.proposedAt = proposedAt;
    }

    public void markConfirmed(long resultingAVersion, long resultingBVersion, Instant at) {
        this.status = SwapStatus.CONFIRMED;
        this.confirmedAt = at;
        this.resultingComboAVersion = resultingAVersion;
        this.resultingComboBVersion = resultingBVersion;
    }

    public void markRejected() {
        this.status = SwapStatus.REJECTED;
    }

    public Long getId() {
        return id;
    }

    public String getBizNo() {
        return bizNo;
    }

    public String getContentFingerprint() {
        return contentFingerprint;
    }

    public SwapStatus getStatus() {
        return status;
    }

    public DutyCombo getComboA() {
        return comboA;
    }

    public DutyCombo getComboB() {
        return comboB;
    }

    public long getComboAVersion() {
        return comboAVersion;
    }

    public long getComboBVersion() {
        return comboBVersion;
    }

    public List<SwapItem> getItems() {
        return items;
    }

    public Instant getProposedAt() {
        return proposedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Long getResultingComboAVersion() {
        return resultingComboAVersion;
    }

    public Long getResultingComboBVersion() {
        return resultingComboBVersion;
    }
}
