package com.naleul.naleul.domain.chart.dto;

// domain/report/dto/WeeklyReportDto.java (최종 조립본 — API 응답용)

import com.naleul.naleul.domain.chart.dto.ChartResponseDto;
import com.naleul.naleul.domain.chart.dto.GoalWeeklyRankingDto;
import com.naleul.naleul.domain.chart.dto.WeekOverWeekDto;
import com.naleul.naleul.domain.user.enums.UserPatternType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class WeeklyReportDto {
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private UserPatternType patternType;

    private AiFeedbackDto aiFeedback;
    private TimePatternAnalysisDto timePattern;
    private RoutineAchievementWeeklyDto routineAchievement;
    private ChartResponseDto goalTimeDistribution;
    private GoalWeeklyRankingDto goalRanking;
    private WeekOverWeekDto weekOverWeek;
}