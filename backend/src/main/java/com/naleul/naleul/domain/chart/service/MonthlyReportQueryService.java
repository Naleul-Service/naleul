package com.naleul.naleul.domain.chart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naleul.naleul.domain.chart.dto.ChartResponseDto;
import com.naleul.naleul.domain.chart.dto.*;
import com.naleul.naleul.domain.chart.entity.MonthlyReport;
import com.naleul.naleul.domain.chart.repository.MonthlyReportRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonthlyReportQueryService {

    private static final double CV_THRESHOLD = 0.3;
    private static final double LOW_RATE_THRESHOLD = 30.0;

    private final MonthlyReportRepository monthlyReportRepository;
    private final ObjectMapper objectMapper;

    // ── 목록 조회 ──────────────────────────────────────

    public List<MonthlyReportSummary> getReportSummaries(Long userId) {
        return monthlyReportRepository.findAllByUserUserIdOrderByPeriodStartDateDesc(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    private MonthlyReportSummary toSummary(MonthlyReport report) {
        LocalDate start = report.getPeriodStartDate();
        int year = start.getYear() % 100;
        int month = start.getMonthValue();
        String label = String.format("%02d년 %d월 패턴 분석", year, month);

        return MonthlyReportSummary.builder()
                .monthlyReportId(report.getMonthlyReportId())
                .periodStartDate(start)
                .periodEndDate(report.getPeriodEndDate())
                .label(label)
                .build();
    }

    // ── 상세 조회 ──────────────────────────────────────

    public MonthlyReportDto getReportDetail(Long userId, LocalDate periodStartDate) {
        MonthlyReport report = findReport(userId, periodStartDate);

        return MonthlyReportDto.builder()
                .periodStartDate(report.getPeriodStartDate())
                .periodEndDate(report.getPeriodEndDate())
                .patternType(report.getPatternTypeSnapshot())
                .aiFeedback(AiFeedbackDto.builder()
                        .title(report.getAiFeedbackTitle())
                        .content(report.getAiFeedbackContent())
                        .build())
                .timePattern(fromJson(report.getTimePatternJson(), TimePatternAnalysisDto.class))
                .weeklyTaskCounts(fromJsonList(report.getWeeklyTaskCountJson(), WeeklyTaskCountDto.class))
                .routineWeekday(fromJsonList(report.getRoutineWeekdayJson(), WeekdayCountDto.class))
                .routineRanking(fromJson(report.getRoutineRankingJson(), RoutineRankingDto.class))
                .goalTimeDistribution(fromJson(report.getGoalTimeDistributionJson(), ChartResponseDto.class))
                .goalDeviations(fromJsonList(report.getGoalDeviationJson(), GoalDeviationItemDto.class))
                .goalWeeklySeries(fromJsonList(report.getGoalWeeklySeriesJson(), GoalWeeklySeriesItemDto.class))
                .build();
    }

    // ── ⑨ 진단 기반 목표 추천 ────────────────────────────

    public GoalRecommendationDto getGoalRecommendation(Long userId, LocalDate periodStartDate) {
        MonthlyReport report = findReport(userId, periodStartDate);

        List<GoalDeviationItemDto> deviations = fromJsonList(report.getGoalDeviationJson(), GoalDeviationItemDto.class);
        List<GoalWeeklySeriesItemDto> series = fromJsonList(report.getGoalWeeklySeriesJson(), GoalWeeklySeriesItemDto.class);

        Map<Long, GoalWeeklySeriesItemDto> seriesMap = series.stream()
                .collect(Collectors.toMap(GoalWeeklySeriesItemDto::getGoalCategoryId, s -> s));

        List<GoalRecommendationItemDto> keep = new ArrayList<>();
        List<GoalRecommendationItemDto> improve = new ArrayList<>();

        for (GoalDeviationItemDto d : deviations) {
            GoalWeeklySeriesItemDto s = seriesMap.get(d.getGoalCategoryId());
            if (s == null) continue;

            double planCV = calcCVFromList(s.getWeeklyPlanRates());
            double routineCV = calcCVFromList(s.getWeeklyRoutineRates());
            double avgPlanRate = s.getWeeklyPlanRates().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double timeCV = d.getCoefficientOfVariation();

            boolean isConsistent = timeCV <= CV_THRESHOLD && planCV <= CV_THRESHOLD
                    && routineCV <= CV_THRESHOLD && avgPlanRate >= LOW_RATE_THRESHOLD;

            if (isConsistent) {
                keep.add(GoalRecommendationItemDto.builder()
                        .goalCategoryId(d.getGoalCategoryId())
                        .goalCategoryName(d.getGoalCategoryName())
                        .reason("계획 실천률, 루틴 실천률, 시간 모두 일관성을 유지했어요.")
                        .build());
            } else {
                improve.add(GoalRecommendationItemDto.builder()
                        .goalCategoryId(d.getGoalCategoryId())
                        .goalCategoryName(d.getGoalCategoryName())
                        .reason(buildImproveReason(timeCV, planCV, routineCV, avgPlanRate))
                        .build());
            }
        }

        return GoalRecommendationDto.builder().keepGoals(keep).improveGoals(improve).build();
    }

    private String buildImproveReason(double timeCV, double planCV, double routineCV, double avgPlanRate) {
        if (avgPlanRate < LOW_RATE_THRESHOLD) {
            return "실천률 자체가 낮아요. 목표 난이도나 분량 조정이 필요해 보여요.";
        }
        if (planCV > CV_THRESHOLD) {
            return "계획 실천률의 편차가 커요. 계획을 잘게 쪼개거나 여유롭게 잡는 게 좋아보여요.";
        }
        if (timeCV > CV_THRESHOLD) {
            return "투입 시간의 편차가 커서 몰아서 진행하는 패턴을 보여요.";
        }
        if (routineCV > CV_THRESHOLD) {
            return "루틴 실천률의 편차가 커요.";
        }
        return "실천 패턴이 불규칙해요.";
    }

    private double calcCVFromList(List<Double> values) {
        double[] arr = values.stream().mapToDouble(Double::doubleValue).toArray();
        double mean = Arrays.stream(arr).average().orElse(0.0);
        if (mean == 0) return 0.0;
        double variance = Arrays.stream(arr).map(v -> Math.pow(v - mean, 2)).average().orElse(0.0);
        return Math.sqrt(variance) / mean;
    }

    // ── 공통 ──────────────────────────────────────────

    private MonthlyReport findReport(Long userId, LocalDate periodStartDate) {
        return monthlyReportRepository.findByUserUserIdAndPeriodStartDate(userId, periodStartDate)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
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