package com.chris64233.crewduty.service;

/**
 * 资格失败规则分类，调用方可据此进行机器判读与归类展示。
 */
public enum FailureRule {
    /** 岗位不匹配：成员岗位不能覆盖值勤段所需岗位 */
    POSITION_MISMATCH,
    /** 机型资质缺失：成员不具备该值勤段机型的执飞资质 */
    AIRCRAFT_NOT_QUALIFIED,
    /** 资质已过期：值勤出发日晚于成员资质有效截止日 */
    QUALIFICATION_EXPIRED,
    /** 值勤时长超限：组合首段出发至末段到达超过上限 */
    DUTY_TIME_EXCEEDED,
    /** 休息不足：同一成员相邻两段值勤间隔低于最低休息要求 */
    INSUFFICIENT_REST,
    /** 值勤段时间不合法（到达早于出发、段间时间重叠/乱序） */
    SEGMENT_TIME_INVALID
}
