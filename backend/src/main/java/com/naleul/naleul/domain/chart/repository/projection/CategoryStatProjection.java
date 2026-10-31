package com.naleul.naleul.domain.chart.repository.projection;

public interface CategoryStatProjection {
    Long getCategoryId();
    String getCategoryName();
    String getColorHex();
    Long getTotalMinutes();
}