// domain/chart/repository/projection/RoutineTimeProjection.java
package com.naleul.naleul.domain.chart.repository.projection;

public interface RoutineTimeProjection {
    Long getRoutineId();
    String getRoutineName();
    Long getGeneralCategoryId();
    Long getTotalMinutes();
}