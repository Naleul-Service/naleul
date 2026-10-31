package com.naleul.naleul.domain.chart.repository.projection;

import java.time.LocalDate;

public interface RoutineDailyCompletionProjection {
    Long getRoutineId();
    String getRoutineName();
    LocalDate getRoutineDate();
    String getTaskStatus();  // TODO/COMPLETED 등
}