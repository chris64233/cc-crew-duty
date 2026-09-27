package com.chris64233.crewduty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

import java.time.Instant;

/**
 * 每次组合调整（发布、发布拒绝、交换提交、确认、拒绝、失效）的审计记录。
 */
@Entity
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作类型，例如 PUBLISH、SWAP_CONFIRM。 */
    @Column(nullable = false)
    private String action;

    /** 受影响的组合 ID（交换涉及双方时各写一条）。 */
    @Column
    private Long pairingId;

    @Lob
    @Column(nullable = false)
    private String detail;

    @Column(nullable = false)
    private Instant createdAt;

    protected AuditRecord() {
    }

    public AuditRecord(String action, Long pairingId, String detail) {
        this.action = action;
        this.pairingId = pairingId;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public Long getPairingId() {
        return pairingId;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
