package com.naleul.naleul.domain.admin.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AdminDashboardProperties.class)
public class AdminDashboardConfig {
}
