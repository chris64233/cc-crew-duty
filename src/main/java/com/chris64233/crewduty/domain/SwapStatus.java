package com.chris64233.crewduty.domain;

/**
 * 交换方案生命周期状态。
 */
public enum SwapStatus {
    /** 已提交，待确认 */
    PROPOSED,
    /** 已确认，成员交换完成 */
    CONFIRMED,
    /** 因版本变化或重新校验失败而失效 */
    REJECTED
}
