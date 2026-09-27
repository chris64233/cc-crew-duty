package com.chris64233.crewduty.domain;

/**
 * 审计动作类型，覆盖组合发布、交换方案提交/确认/拒绝。
 */
public enum AuditAction {
    COMBO_PUBLISHED,
    SWAP_PROPOSED,
    SWAP_CONFIRMED,
    SWAP_REJECTED
}
