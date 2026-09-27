package com.chris64233.crewduty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

import java.time.Instant;

/**
 * 幂等记录：业务号 + 操作类型 + 请求内容指纹 + 原始响应。
 * 相同业务号内容一致时重放原结果，内容不一致时拒绝。
 */
@Entity
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 调用方提供的业务号，全局唯一。 */
    @Column(nullable = false, unique = true)
    private String idemKey;

    @Column(nullable = false)
    private String operation;

    /** 请求关键内容的指纹，用于判断是否同一请求。 */
    @Column(nullable = false)
    private String requestFingerprint;

    @Column(nullable = false)
    private int httpStatus;

    @Lob
    @Column(nullable = false)
    private String responseBody;

    @Column(nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String idemKey, String operation, String requestFingerprint,
                             int httpStatus, String responseBody) {
        this.idemKey = idemKey;
        this.operation = operation;
        this.requestFingerprint = requestFingerprint;
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getIdemKey() {
        return idemKey;
    }

    public String getOperation() {
        return operation;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
