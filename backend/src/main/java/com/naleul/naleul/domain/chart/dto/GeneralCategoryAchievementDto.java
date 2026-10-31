// domain/chart/dto/GeneralCategoryAchievementDto.java
package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GeneralCategoryAchievementDto {
    private Long generalCategoryId;
    private String generalCategoryName;
    private String colorHex;
    private Double achievementRate;   // (루틴+계획) Task 실천률
    private Integer achievedCount;
    private Integer totalCount;
    private List<RoutineAchievementDto> routines;
}