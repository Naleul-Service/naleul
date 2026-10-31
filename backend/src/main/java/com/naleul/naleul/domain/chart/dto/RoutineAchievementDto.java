// domain/chart/dto/RoutineAchievementDto.java
package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class RoutineAchievementDto {
    private Long routineId;
    private String routineName;
    private Double achievementRate;
    private Integer achievedCount;
    private Integer totalCount;
    private Integer currentStreak;
    private Integer longestStreak;
    private List<String> repeatDays;         // 히트맵 요일 렌더링용
    private List<LocalDate> completedDates;  // 히트맵 색칠할 날짜들
}