package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * GET /api/v1/charts/goal-categories/{goalCategoryId}/pattern 응답 DTO.
 *
 * 완료된 목표(GoalCompletionReport 존재)만 실제 패턴 데이터를 채워 내려줍니다.
 * 완료되지 않은 목표는 isCompleted=false, 나머지 필드는 null 상태로 200 응답합니다.
 * (404 에러 대신 플래그로 처리 — iOS에서 에러 상태와 "미완료" 상태를 분리할 필요 없게 하기 위함)
 */
@Getter
@Builder
public class GoalPatternDetailDto {
    private boolean isCompleted;
    private TimePatternAnalysisDto timePattern;
    private List<WeekdayCountDto> weekdayPattern;
    private List<PeriodPatternItemDto> periodPattern;
}