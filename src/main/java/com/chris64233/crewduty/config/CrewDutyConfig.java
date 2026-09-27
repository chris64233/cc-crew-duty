package com.chris64233.crewduty.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DutyRuleProperties.class)
public class CrewDutyConfig {
}
