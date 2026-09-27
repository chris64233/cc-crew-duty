package com.chris64233.crewduty.domain;

/**
 * 组合发布状态。发布失败时不产生组合，因此只有“已发布”这一正常状态；
 * 已发布组合经交换确认后内容会更新，但状态保持不变。
 */
public enum ComboStatus {
    PUBLISHED
}
