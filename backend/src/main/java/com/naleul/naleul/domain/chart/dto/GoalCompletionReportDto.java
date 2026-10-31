package com.naleul.naleul.domain.chart.dto;

import com.naleul.naleul.domain.chart.dto.*;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class GoalCompletionReportDto {
    // 헤더
    private Long goalCategoryId;
    private String goalCategoryName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String achievement;
    private String achievementImageUrl;

    private long daysTogether;
    private long totalMinutes;
    private int executionCount;      // 계획+루틴 전체 Task 개수
    private double achievementRate;  // 계획+루틴 통합 달성률

    // ① 시간 패턴 — 기존 TimePatternAnalysisDto 재사용
    private TimePatternAnalysisDto timePattern;

    // ② 요일 패턴 — 기존 WeekdayCountDto 재사용
    private List<WeekdayCountDto> weekdayPattern;

    // ③ 기간 7등분 — 신규
    private List<PeriodPatternItemDto> periodPattern;

    // ④ 세부목표 시간배분 — 기존 ChartResponseDto 재사용
    private ChartResponseDto generalCategoryTimeDistribution;

    // ⑤ 세부목표 랭킹 — 신규
    private GeneralCategoryRankingDto generalCategoryRanking;

    // ⑥ 루틴 랭킹 — 기존 RoutineRankingDto 재사용
    private RoutineRankingDto routineRanking;

    // ⑦ 세부목표별 실천률 — 기존 GeneralCategoryAchievementDto 재사용
    private List<GeneralCategoryAchievementDto> generalCategoryAchievements;
}