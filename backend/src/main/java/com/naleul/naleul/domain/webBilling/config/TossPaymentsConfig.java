package com.naleul.naleul.domain.webBilling.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({TossPaymentsProperties.class, TossPayProperties.class})
public class TossPaymentsConfig {
}
