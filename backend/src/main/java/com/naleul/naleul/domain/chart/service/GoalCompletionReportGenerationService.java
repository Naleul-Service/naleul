package com.naleul.naleul.domain.chart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naleul.naleul.domain.chart.GeneralCategoryRankItemDto;
import com.naleul.naleul.domain.chart.dto.*;
import com.naleul.naleul.domain.chart.repository.ChartRepository;
import com.naleul.naleul.domain.chart.repository.projection.AchievementStatProjection;
import com.naleul.naleul.domain.chart.repository.projection.CategoryStatProjection;
import com.naleul.naleul.domain.chart.repository.projection.GeneralCategoryExecutionStatProjection;
import com.naleul.naleul.domain.chart.repository.projection.RoutineAchievementStatProjection;
import com.naleul.naleul.domain.chart.service.ChartService;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.chart.dto.*;
import com.naleul.naleul.domain.chart.entity.GoalCompletionReport;
import com.naleul.naleul.domain.chart.repository.GoalCompletionReportRepository;
import com.naleul.naleul.domain.chart.repository.projection.HourlyStatProjection;
import com.naleul.naleul.domain.chart.repository.projection.WeekdayCountProjection;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.routine.util.RoutineStreakCalculator;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.task.repository.projection.DateCountProjection;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoalCompletionReportGenerationService {

    private final TaskRepository taskRepository;
    private final ChartRepository chartRepository;
    private final RoutineRepository routineRepository;
    private final ChartService chartService;
    private final GoalCompletionReportRepository goalCompletionReportRepository;
    private final GoalCategoryRepository goalCategoryRepository; // ✅ 신규: goalCategoryId로 직접 조회하기 위해 추가
    private final ObjectMapper objectMapper;

    private static final List<String> WEEKDAY_NAMES = List.of(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
    );
    private static final int PERIOD_SEGMENTS = 7;

    /**
     * ✅ 변경: GoalCategory 객체 대신 goalCategoryId(Long)를 받아서 이 메서드 안에서 직접 조회.
     * 이벤트 리스너의 "새로운 트랜잭션" 안에서 호출되므로, 이전 트랜잭션에서 조회했던
     * (이미 준영속 상태가 됐을 수 있는) 엔티티를 그대로 재사용하면 안 되고 여기서 새로 조회해야 함.
     */
    @Transactional
    public void generateReport(Long goalCategoryId) {
        if (goalCompletionReportRepository.existsByGoalCategoryGoalCategoryId(goalCategoryId)) {
            log.info("이미 완료 리포트가 존재해 스킵: goalCategoryId={}", goalCategoryId);
            return;
        }

        GoalCategory goalCategory = goalCategoryRepository.findByIdWithAll(goalCategoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.GOAL_CATEGORY_NOT_FOUND));

        LocalDate startDate = goalCategory.getGoalCategoryStartDate();
        LocalDate endDate = goalCategory.getGoalCategoryEndDate();

        // ── 헤더: 계획+루틴 통합 실천 횟수 & 달성률 ──
        AchievementStatProjection overallStat = taskRepository.findGoalCategoryOverallAchievementStats(goalCategoryId);
        long executionCount = overallStat.getTotalCount() != null ? overallStat.getTotalCount() : 0L;
        long achievedCount = overallStat.getAchievedCount() != null ? overallStat.getAchievedCount() : 0L;
        double achievementRate = calcPercentage(achievedCount, executionCount);

        long daysTogether = (startDate != null && endDate != null)
                ? ChronoUnit.DAYS.between(startDate, endDate) + 1
                : 0;

        // ── ④ 세부목표 시간배분 (완전 재사용 — 기간 없이 goalCategory 전체) ──
        List<CategoryStatProjection> generalStats =
                chartRepository.findGeneralCategoryStatsByGoalCategory(goalCategoryId, null, null);
        ChartResponseDto generalCategoryTimeDistribution = buildChartResponse(generalStats);
        long totalMinutes = generalStats.stream().mapToLong(CategoryStatProjection::getTotalMinutes).sum();

        // ── ① 시간 패턴 ──
        TimePatternAnalysisDto timePattern = buildTimePattern(goalCategoryId);

        // ── ② 요일 패턴 ──
        List<WeekdayCountDto> weekdayPattern = buildWeekdayPattern(goalCategoryId);

        // ── ③ 기간 7등분 ──
        List<PeriodPatternItemDto> periodPattern = buildPeriodPattern(goalCategoryId, startDate, endDate);

        // ── ⑤ 세부목표 랭킹 ──
        GeneralCategoryRankingDto generalCategoryRanking = buildGeneralCategoryRanking(goalCategoryId);

        // ── ⑥ 루틴 랭킹 (완전 재사용) ──
        RoutineRankingDto routineRanking = buildRoutineRanking(goalCategoryId);

        // ── ⑦ 세부목표별 실천률 (완전 재사용) ──
        GoalCategoryAchievementStatisticsDto achievementStats =
                chartService.getGoalCategoryAchievementStatistics(
                        goalCategory.getUser().getUserId(), goalCategoryId, null, null);
        List<GeneralCategoryAchievementDto> generalCategoryAchievements = achievementStats.getGeneralCategories();

        GoalCompletionReport report = GoalCompletionReport.builder()
                .user(goalCategory.getUser())
                .goalCategory(goalCategory)
                .goalCategoryNameSnapshot(goalCategory.getGoalCategoryName())
                .achievementSnapshot(goalCategory.getAchievement())
                .achievementImageUrlSnapshot(goalCategory.getAchievementImageUrl())
                .startDate(startDate)
                .endDate(endDate)
                .daysTogether(daysTogether)
                .totalMinutes(totalMinutes)
                .executionCount((int) executionCount)
                .achievementRate(achievementRate)
                .timePatternJson(toJson(timePattern))
                .weekdayPatternJson(toJson(weekdayPattern))
                .periodPatternJson(toJson(periodPattern))
                .generalCategoryTimeJson(toJson(generalCategoryTimeDistribution))
                .generalCategoryRankingJson(toJson(generalCategoryRanking))
                .routineRankingJson(toJson(routineRanking))
                .generalCategoryAchievementJson(toJson(generalCategoryAchievements))
                .build();

        goalCompletionReportRepository.save(report);
        log.info("목표 완료 리포트 생성 완료: goalCategoryId={}", goalCategoryId);
    }

    // ── ① 시간 패턴 ────────────────────────────────────

    private TimePatternAnalysisDto buildTimePattern(Long goalCategoryId) {
        List<HourlyStatProjection> stats = taskRepository.findHourlyPatternStatsByGoalCategory(goalCategoryId);
        if (stats.isEmpty()) {
            return TimePatternAnalysisDto.builder().hourlyBreakdown(List.of()).build();
        }

        long totalAllHours = stats.stream().mapToLong(HourlyStatProjection::getTotalCount).sum();

        List<HourlyPatternDto> breakdown = stats.stream()
                .map(s -> {
                    long total = s.getTotalCount();
                    long achieved = s.getAchievedCount() != null ? s.getAchievedCount() : 0L;
                    return HourlyPatternDto.builder()
                            .hour(s.getHour())
                            .totalCount(total)
                            .achievedCount(achieved)
                            .achievementRate(calcPercentage(achieved, total))
                            .build();
                })
                .sorted(Comparator.comparingInt(HourlyPatternDto::getHour))
                .toList();

        HourlyPatternDto best = breakdown.stream()
                .max(Comparator.comparingDouble(HourlyPatternDto::getAchievementRate))
                .orElse(null);
        HourlyPatternDto worst = breakdown.stream()
                .min(Comparator.comparingDouble(HourlyPatternDto::getAchievementRate))
                .orElse(null);

        return TimePatternAnalysisDto.builder()
                .bestHour(best != null ? best.getHour() : null)
                .bestRate(best != null ? best.getAchievementRate() : null)
                .bestRatioOfTotal(best != null ? calcPercentage(best.getTotalCount(), totalAllHours) : null)
                .worstHour(worst != null ? worst.getHour() : null)
                .worstRate(worst != null ? worst.getAchievementRate() : null)
                .worstRatioOfTotal(worst != null ? calcPercentage(worst.getTotalCount(), totalAllHours) : null)
                .hourlyBreakdown(breakdown)
                .build();
    }

    // ── ② 요일 패턴 ────────────────────────────────────

    private List<WeekdayCountDto> buildWeekdayPattern(Long goalCategoryId) {
        List<WeekdayCountProjection> stats = taskRepository.findWeekdayPatternStatsByGoalCategory(goalCategoryId);
        Map<Integer, Long> countByWeekday = stats.stream()
                .collect(Collectors.toMap(WeekdayCountProjection::getWeekday, WeekdayCountProjection::getCount));

        List<WeekdayCountDto> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            result.add(WeekdayCountDto.builder()
                    .weekday(WEEKDAY_NAMES.get(i))
                    .count(countByWeekday.getOrDefault(i, 0L))
                    .build());
        }
        return result;
    }

    // ── ③ 기간 7등분 ───────────────────────────────────

    private List<PeriodPatternItemDto> buildPeriodPattern(Long goalCategoryId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return List.of();
        }

        List<DateCountProjection> dailyCounts = taskRepository.findDailyTaskCountsByGoalCategory(goalCategoryId);
        Map<LocalDate, Long> countByDate = dailyCounts.stream()
                .collect(Collectors.toMap(DateCountProjection::getTaskDate, DateCountProjection::getCount));

        long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        double daysPerSegment = (double) totalDays / PERIOD_SEGMENTS;

        long[] segmentCounts = new long[PERIOD_SEGMENTS];

        for (Map.Entry<LocalDate, Long> entry : countByDate.entrySet()) {
            LocalDate date = entry.getKey();
            if (date.isBefore(startDate) || date.isAfter(endDate)) continue;

            long dayOffset = ChronoUnit.DAYS.between(startDate, date);
            int segmentIndex = (int) (dayOffset / daysPerSegment);
            if (segmentIndex >= PERIOD_SEGMENTS) segmentIndex = PERIOD_SEGMENTS - 1;

            segmentCounts[segmentIndex] += entry.getValue();
        }

        List<PeriodPatternItemDto> result = new ArrayList<>();
        for (int i = 0; i < PERIOD_SEGMENTS; i++) {
            result.add(PeriodPatternItemDto.builder()
                    .segmentIndex(i + 1)
                    .taskCount(segmentCounts[i])
                    .build());
        }
        return result;
    }

    // ── ⑤ 세부목표 랭킹 ─────────────────────────────────

    private GeneralCategoryRankingDto buildGeneralCategoryRanking(Long goalCategoryId) {
        List<GeneralCategoryExecutionStatProjection> stats =
                taskRepository.findGeneralCategoryExecutionStats(goalCategoryId);

        if (stats.isEmpty()) {
            return GeneralCategoryRankingDto.builder().build();
        }

        List<GeneralCategoryRankItemDto> items = stats.stream()
                .map(s -> GeneralCategoryRankItemDto.builder()
                        .generalCategoryId(s.getGeneralCategoryId())
                        .generalCategoryName(s.getGeneralCategoryName())
                        .achievedCount(s.getAchievedCount() != null ? s.getAchievedCount() : 0L)
                        .build())
                .toList();

        return GeneralCategoryRankingDto.builder()
                .mostExecutedGeneralCategory(items.stream().max(Comparator.comparingLong(GeneralCategoryRankItemDto::getAchievedCount)).orElse(null))
                .leastExecutedGeneralCategory(items.stream().min(Comparator.comparingLong(GeneralCategoryRankItemDto::getAchievedCount)).orElse(null))
                .build();
    }

    // ── ⑥ 루틴 랭킹 (완전 재사용 조합) ───────────────────────

    private RoutineRankingDto buildRoutineRanking(Long goalCategoryId) {
        List<RoutineAchievementStatProjection> achievementStats =
                taskRepository.findRoutineAchievementStatsGroupedByRoutine(goalCategoryId, null, null);

        if (achievementStats.isEmpty()) {
            return RoutineRankingDto.builder().build();
        }

        List<Routine> routines = routineRepository.findAllByGoalCategoryId(goalCategoryId);
        Map<Long, Routine> routineMap = routines.stream()
                .collect(Collectors.toMap(Routine::getRoutineId, r -> r));

        List<Long> routineIds = routines.stream().map(Routine::getRoutineId).toList();
        Map<Long, List<LocalDate>> completedDatesByRoutine = routineIds.isEmpty()
                ? Map.of()
                : taskRepository.findCompletedDatesGroupedByRoutineId(routineIds);

        List<RoutineRankItemDto> items = achievementStats.stream()
                .map(s -> {
                    Long routineId = s.getRoutineId();
                    Routine routine = routineMap.get(routineId);
                    long achieved = s.getAchievedCount() != null ? s.getAchievedCount() : 0L;

                    int longestStreak = routine != null
                            ? RoutineStreakCalculator.calculateLongestStreak(
                            routine, completedDatesByRoutine.getOrDefault(routineId, List.of()))
                            : 0;

                    return RoutineRankItemDto.builder()
                            .routineId(routineId)
                            .routineName(routine != null ? routine.getRoutineName() : "")
                            .executedCount(achieved)
                            .longestStreak(longestStreak)
                            .build();
                })
                .toList();

        return RoutineRankingDto.builder()
                .mostExecutedRoutine(items.stream().max(Comparator.comparingLong(RoutineRankItemDto::getExecutedCount)).orElse(null))
                .leastExecutedRoutine(items.stream().min(Comparator.comparingLong(RoutineRankItemDto::getExecutedCount)).orElse(null))
                .highestStreakRoutine(items.stream().max(Comparator.comparingInt(RoutineRankItemDto::getLongestStreak)).orElse(null))
                .lowestStreakRoutine(items.stream().min(Comparator.comparingInt(RoutineRankItemDto::getLongestStreak)).orElse(null))
                .build();
    }

    // ── 공통 유틸 ─────────────────────────────────────────

    private ChartResponseDto buildChartResponse(List<CategoryStatProjection> stats) {
        long total = stats.stream().mapToLong(CategoryStatProjection::getTotalMinutes).sum();
        List<ChartSliceDto> slices = stats.stream()
                .map(p -> ChartSliceDto.builder()
                        .id(p.getCategoryId())
                        .name(p.getCategoryName())
                        .colorHex(p.getColorHex())
                        .totalMinutes(p.getTotalMinutes())
                        .percentage(calcPercentage(p.getTotalMinutes(), total))
                        .build())
                .toList();
        return ChartResponseDto.builder().totalMinutes(total).slices(slices).build();
    }

    private double calcPercentage(long part, long total) {
        if (total == 0) return 0.0;
        return Math.round((double) part / total * 1000.0) / 10.0;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON 직렬화 실패", e);
        }
    }
}