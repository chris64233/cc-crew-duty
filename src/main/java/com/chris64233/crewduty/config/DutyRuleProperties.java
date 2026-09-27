package com.chris64233.crewduty.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 值勤规则参数，可通过 application.properties / 环境变量覆盖。
 *
 * @param maxDutyHours     单个值勤组合的值勤时长上限（首段出发到末段到达），默认 14 小时
 * @param minRestHours     同一成员相邻两段值勤之间的最低休息小时数，默认 10 小时
 */
@ConfigurationProperties(prefix = "crewduty.rule")
public record DutyRuleProperties(Duration maxDutyHours, Duration minRestHours) {

    public DutyRuleProperties {
        if (maxDutyHours == null) {
            maxDutyHours = Duration.ofHours(14);
        }
        if (minRestHours == null) {
            minRestHours = Duration.ofHours(10);
        }
    }

    /** 全默认参数，供不加载 Spring 配置的单元测试使用 */
    public static DutyRuleProperties defaults() {
        return new DutyRuleProperties(Duration.ofHours(14), Duration.ofHours(10));
    }
}
