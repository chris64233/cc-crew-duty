package com.chris64233.crewduty.domain;

/**
 * 成员交换方案状态。
 */
public enum SwapStatus {
    /** 已提交，等待确认。 */
    PENDING,
    /** 已确认，交换在一次事务中生效。 */
    CONFIRMED,
    /** 确认时重新校验未通过，交换未生效。 */
    REJECTED,
    /** 任一组合版本已变化，方案失效。 */
    STALE
}
