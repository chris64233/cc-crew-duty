package com.chris64233.crewduty.domain;

/**
 * 值勤组合状态。
 */
public enum PairingStatus {
    /** 草稿：已创建但未发布，不参与他人休息校验。 */
    DRAFT,
    /** 已发布：资格校验全部通过，对成员生效。 */
    PUBLISHED
}
