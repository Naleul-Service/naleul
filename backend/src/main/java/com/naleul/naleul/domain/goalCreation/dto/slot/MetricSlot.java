package com.naleul.naleul.domain.goalCreation.dto.slot;

import java.math.BigDecimal;

/** 예: { "name": "체중", "currentValue": 78, "targetValue": 68, "unit": "kg" } */
public record MetricSlot(String name, BigDecimal currentValue, BigDecimal targetValue, String unit) {
}
