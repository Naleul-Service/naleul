package com.naleul.naleul.domain.chart.repository.projection;

public interface HourlyStatProjection {
    Integer getHour();        // 0~23
    Long getTotalCount();
    Long getAchievedCount();
}