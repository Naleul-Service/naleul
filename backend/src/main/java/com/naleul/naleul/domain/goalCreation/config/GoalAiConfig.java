package com.naleul.naleul.domain.goalCreation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** GoalAiProperties를 빈으로 등록 (record + @ConfigurationProperties는 이 등록이 있어야 주입됨) */
@Configuration
@EnableConfigurationProperties(GoalAiProperties.class)
public class GoalAiConfig {
}
