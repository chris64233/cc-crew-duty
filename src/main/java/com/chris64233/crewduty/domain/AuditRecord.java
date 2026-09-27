package com.chris64233.crewduty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 组合发布、交换方案提交/确认/拒绝的审计记录。
 *
 * <p>每次调整追加一条，不修改、不删除；{@code detail} 以 JSON 保存变更摘要，
 * 如发布的成员快照、交换前后的段成员对照等。
 */
@Entity
@Table(name = "audit_record")
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    /** 关联的业务号（发布号或交换确认号） */
    @Column(nullable = false)
    private String bizNo;

    /** 受影响组合 id，可能为单个或多个，以逗号分隔 */
    @Column(length = 64)
    private String comboIds;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    @Lob
    @Column(nullable = false)
    private String detail;

    protected AuditRecord() {
    }

    public AuditRecord(AuditAction action, String bizNo, String comboIds,
                       Instant occurredAt, String detail) {
        this.action = action;
        this.bizNo = bizNo;
        this.comboIds = comboIds;
        this.occurredAt = occurredAt;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getBizNo() {
        return bizNo;
    }

    public String getComboIds() {
        return comboIds;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getDetail() {
        return detail;
    }
}
