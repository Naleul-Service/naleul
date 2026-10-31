package com.naleul.naleul.domain.chart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naleul.naleul.domain.chart.dto.*;
import com.naleul.naleul.domain.chart.dto.GoalCompletionReportDto;
import com.naleul.naleul.domain.chart.dto.GoalPatternDetailDto;
import com.naleul.naleul.domain.chart.dto.PeriodPatternItemDto;
import com.naleul.naleul.domain.chart.dto.RoutineRankingDto;
import com.naleul.naleul.domain.chart.dto.TimePatternAnalysisDto;
import com.naleul.naleul.domain.chart.dto.WeekdayCountDto;
import com.naleul.naleul.domain.chart.entity.GoalCompletionReport;
import com.naleul.naleul.domain.chart.repository.GoalCompletionReportRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoalCompletionReportQueryService {

    private final GoalCompletionReportRepository goalCompletionReportRepository;
    private final ObjectMapper objectMapper;

    public GoalCompletionReportDto getReport(Long goalCategoryId) {
        GoalCompletionReport report = goalCompletionReportRepository.findByGoalCategoryGoalCategoryId(goalCategoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        return GoalCompletionReportDto.builder()
                .goalCategoryId(report.getGoalCategory().getGoalCategoryId())
                .goalCategoryName(report.getGoalCategoryNameSnapshot())
                .startDate(report.getStartDate())
                .endDate(report.getEndDate())
                .achievement(report.getAchievementSnapshot())
                .achievementImageUrl(report.getAchievementImageUrlSnapshot())
                .daysTogether(report.getDaysTogether())
                .totalMinutes(report.getTotalMinutes())
                .executionCount(report.getExecutionCount())
                .achievementRate(report.getAchievementRate())
                .timePattern(fromJson(report.getTimePatternJson(), TimePatternAnalysisDto.class))
                .weekdayPattern(fromJsonList(report.getWeekdayPatternJson(), WeekdayCountDto.class))
                .periodPattern(fromJsonList(report.getPeriodPatternJson(), PeriodPatternItemDto.class))
                .generalCategoryTimeDistribution(fromJson(report.getGeneralCategoryTimeJson(), ChartResponseDto.class))
                .generalCategoryRanking(fromJson(report.getGeneralCategoryRankingJson(), GeneralCategoryRankingDto.class))
                .routineRanking(fromJson(report.getRoutineRankingJson(), RoutineRankingDto.class))
                .generalCategoryAchievements(fromJsonList(report.getGeneralCategoryAchievementJson(), GeneralCategoryAchievementDto.class))
                .build();
    }

    /**
     * "패턴분석" 탭 전용 — 시간/요일/기간 패턴만 뽑아서 내려줌.
     * GoalCompletionReport가 없으면(목표 미완료) isCompleted=false, 나머지는 null.
     * getReport()처럼 CustomException을 던지지 않는 이유: 미완료는 "에러"가 아니라
     * 정상적인 상태이고, iOS 쪽에서 블러+안내 문구를 보여줘야 하기 때문.
     */
    public GoalPatternDetailDto getPatternDetail(Long goalCategoryId) {
        return goalCompletionReportRepository.findByGoalCategoryGoalCategoryId(goalCategoryId)
                .map(report -> GoalPatternDetailDto.builder()
                        .isCompleted(true)
                        .timePattern(fromJson(report.getTimePatternJson(), TimePatternAnalysisDto.class))
                        .weekdayPattern(fromJsonList(report.getWeekdayPatternJson(), WeekdayCountDto.class))
                        .periodPattern(fromJsonList(report.getPeriodPatternJson(), PeriodPatternItemDto.class))
                        .build())
                .orElseGet(() -> GoalPatternDetailDto.builder()
                        .isCompleted(false)
                        .build());
    }

    private <T> T fromJson(String json, Class<T> targetType) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, targetType);
        } catch (Exception e) {
            throw new RuntimeException("JSON 역직렬화 실패: " + targetType.getSimpleName(), e);
        }
    }

    private <T> List<T> fromJsonList(String json, Class<T> elementType) {
        if (json == null) return List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (Exception e) {
            throw new RuntimeException("JSON 리스트 역직렬화 실패", e);
        }
    }
}