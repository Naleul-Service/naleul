package com.naleul.naleul.domain.chart.dto;

// MonthlyReportDto.java
import com.naleul.naleul.domain.chart.dto.ChartResponseDto;
import com.naleul.naleul.domain.user.enums.UserPatternType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class MonthlyReportDto {
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    private UserPatternType patternType;

    private AiFeedbackDto aiFeedback;
    private TimePatternAnalysisDto timePattern;
    private List<WeeklyTaskCountDto> weeklyTaskCounts;
    private List<WeekdayCountDto> routineWeekday;
    private RoutineRankingDto routineRanking;
    private ChartResponseDto goalTimeDistribution;
    private List<GoalDeviationItemDto> goalDeviations;
    private List<GoalWeeklySeriesItemDto> goalWeeklySeries;
}
