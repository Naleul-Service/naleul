package com.naleul.naleul.domain.chart.dto;

// domain/chart/dto/WeekOverWeekDto.java
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WeekOverWeekDto {
    private Long totalMinutesDelta;  // "지난주 대비 2시간 줄었어요"
    private List<WeekOverWeekGoalDto> goalComparisons;
}