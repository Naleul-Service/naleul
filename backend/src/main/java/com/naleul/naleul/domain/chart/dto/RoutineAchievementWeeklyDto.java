package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RoutineAchievementWeeklyDto {
    private List<WeekdayCountDto> lineChart;      // 요일별 총 완료 개수
    private List<RoutineHeatmapRowDto> heatmap;   // 루틴별 x 요일별 완료 여부
}