package com.naleul.naleul.domain.chart.service;

import com.naleul.naleul.domain.chart.dto.*;
import com.naleul.naleul.domain.chart.repository.ChartRepository;
import com.naleul.naleul.domain.chart.repository.projection.*;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.goalCategory.repository.projection.GoalCountProjection;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.routine.util.RoutineStreakCalculator;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChartService {

    private final ChartRepository chartRepository;
    private final TaskRepository taskRepository;
    private final GoalCategoryRepository goalCategoryRepository;
    private final GeneralCategoryRepository generalCategoryRepository;
    private final RoutineRepository routineRepository;

    private static final int KST_OFFSET_HOURS = 9;

    // ── 7-1-1: 전체 goalCategory 차트 (기간 옵션) ─────────────
    // ⚠️ slice마다 goalCategoryStatus를 실어서 내려줌 → iOS에서 상태 필터링 + 재계산

    public ChartResponseDto getGoalCategoryChart(Long userId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = toKstDayStart(startDate);
        LocalDateTime end = toKstDayEnd(endDate);
        List<GoalCategoryStatProjection> stats = chartRepository.findGoalCategoryStats(userId, start, end);
        return buildGoalChartResponse(stats);
    }

    // ── 7-2-1: goalCategory별 generalCategory 차트 (기간 옵션) ──

    public List<GoalCategoryChartDto> getGoalDetailChart(Long userId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = toKstDayStart(startDate);
        LocalDateTime end = toKstDayEnd(endDate);

        List<GoalGeneralStatProjection> stats = chartRepository.findGoalGeneralCategoryStats(userId, start, end);

        Map<Long, List<GoalGeneralStatProjection>> grouped = stats.stream()
                .collect(Collectors.groupingBy(GoalGeneralStatProjection::getGoalCategoryId));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<GoalGeneralStatProjection> group = entry.getValue();
                    GoalGeneralStatProjection first = group.get(0);

                    long goalTotal = group.stream()
                            .mapToLong(GoalGeneralStatProjection::getTotalMinutes)
                            .sum();

                    List<ChartSliceDto> slices = group.stream()
                            .map(p -> ChartSliceDto.builder()
                                    .id(p.getGeneralCategoryId())
                                    .name(p.getGeneralCategoryName())
                                    .colorHex(p.getGeneralColorHex())
                                    .totalMinutes(p.getTotalMinutes())
                                    .percentage(calcPercentage(p.getTotalMinutes(), goalTotal))
                                    .build())
                            .toList();

                    return GoalCategoryChartDto.builder()
                            .goalCategoryId(first.getGoalCategoryId())
                            .goalCategoryName(first.getGoalCategoryName())
                            .colorHex(first.getGoalColorHex())
                            .totalMinutes(goalTotal)
                            .generalCategories(slices)
                            .build();
                })
                .toList();
    }

    // ── 7-3-1: 전체 generalCategory 차트 (기간 옵션) ──────────

    public ChartResponseDto getGeneralCategoryChart(Long userId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = toKstDayStart(startDate);
        LocalDateTime end = toKstDayEnd(endDate);
        List<CategoryStatProjection> stats = chartRepository.findGeneralCategoryStats(userId, start, end);
        return buildChartResponse(stats);
    }

    // ── 계획 달성률 / 루틴 달성률 (기간 옵션) ─────────────────
    // ⚠️ 목표별 breakdown을 같이 내려줘서 iOS가 상태별로 필터링 + 재계산할 수 있게 함

    public AchievementChartDto getPlanAchievementChart(Long userId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = toKstDayStart(startDate);
        LocalDateTime end = toKstDayEnd(endDate);
        List<GoalAchievementStatProjection> grouped =
                taskRepository.findPlanAchievementStatsGroupedByGoal(userId, start, end);
        return toAchievementDtoWithBreakdown(grouped);
    }

    public AchievementChartDto getRoutineAchievementChart(Long userId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = toKstDayStart(startDate);
        LocalDateTime end = toKstDayEnd(endDate);
        List<GoalAchievementStatProjection> grouped =
                taskRepository.findRoutineAchievementStatsGroupedByGoal(userId, start, end);
        return toAchievementDtoWithBreakdown(grouped);
    }

    // ── 목표별 통계 카드 목록 ("목표" 탭) ───────────────────────

    public List<GoalCategoryStatisticsDto> getGoalCategoryStatistics(Long userId, GoalCategoryStatus statusFilter) {
        List<GoalCategory> goalCategories = goalCategoryRepository.findAllByUserIdWithAll(userId);
        if (statusFilter != null) {
            goalCategories = goalCategories.stream()
                    .filter(gc -> gc.getGoalCategoryStatus() == statusFilter)
                    .toList();
        }
        if (goalCategories.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> generalCountMap = toCountMap(generalCategoryRepository.countActiveGroupedByGoalCategory(userId));
        Map<Long, Long> routineCountMap = toCountMap(routineRepository.countActiveGroupedByGoalCategory(userId));
        Map<Long, Long> totalMinutesMap = chartRepository.findGoalCategoryStats(userId, null, null).stream()
                .collect(Collectors.toMap(GoalCategoryStatProjection::getCategoryId, GoalCategoryStatProjection::getTotalMinutes));
        // ⚠️ 여기는 항상 전체 기간 기준이라 startDateTime/endDateTime에 null 전달
        Map<Long, GoalAchievementStatProjection> planStatMap = taskRepository.findPlanAchievementStatsGroupedByGoal(userId, null, null).stream()
                .collect(Collectors.toMap(GoalAchievementStatProjection::getGoalCategoryId, p -> p));
        Map<Long, GoalAchievementStatProjection> routineStatMap = taskRepository.findRoutineAchievementStatsGroupedByGoal(userId, null, null).stream()
                .collect(Collectors.toMap(GoalAchievementStatProjection::getGoalCategoryId, p -> p));

        LocalDate today = LocalDate.now();

        return goalCategories.stream()
                .map(gc -> buildStatisticsDto(gc, generalCountMap, routineCountMap, totalMinutesMap, planStatMap, routineStatMap, today))
                .toList();
    }

    // ── ✅ 목표 상세 - "시간" 탭 (도넛 + 막대) ──────────────────

    public GoalCategoryTimeStatisticsDto getGoalCategoryTimeStatistics(
            Long userId, Long goalCategoryId, LocalDate startDate, LocalDate endDate) {

        GoalCategory gc = findOwnedGoalCategory(userId, goalCategoryId);
        LocalDateTime start = toKstDayStart(startDate);
        LocalDateTime end = toKstDayEnd(endDate);

        // 1단계: goalCategory 안의 generalCategory별 시간 합계 (쿼리 1번)
        List<CategoryStatProjection> generalStats =
                chartRepository.findGeneralCategoryStatsByGoalCategory(goalCategoryId, start, end);

        // 2단계: goalCategory 안의 routine별 시간 합계 (쿼리 1번, 전체 한번에)
        List<RoutineTimeProjection> routineStats =
                chartRepository.findRoutineTimeStatsByGoalCategory(goalCategoryId, start, end);

        // routine들을 소속 generalCategory 기준으로 미리 묶어두기 (N+1 방지)
        Map<Long, List<RoutineTimeProjection>> routinesByGeneral = routineStats.stream()
                .collect(Collectors.groupingBy(RoutineTimeProjection::getGeneralCategoryId));

        long goalTotal = generalStats.stream().mapToLong(CategoryStatProjection::getTotalMinutes).sum();

        List<GeneralCategoryTimeDto> generalDtos = generalStats.stream()
                .map(gStat -> {
                    long generalTotal = gStat.getTotalMinutes();
                    List<RoutineTimeProjection> routines = routinesByGeneral.getOrDefault(gStat.getCategoryId(), List.of());

                    List<RoutineTimeDto> routineDtos = routines.stream()
                            .map(r -> RoutineTimeDto.builder()
                                    .routineId(r.getRoutineId())
                                    .routineName(r.getRoutineName())
                                    .totalMinutes(r.getTotalMinutes())
                                    .percentage(calcPercentage(r.getTotalMinutes(), generalTotal))
                                    .build())
                            .toList();

                    return GeneralCategoryTimeDto.builder()
                            .generalCategoryId(gStat.getCategoryId())
                            .generalCategoryName(gStat.getCategoryName())
                            .colorHex(gStat.getColorHex())
                            .totalMinutes(generalTotal)
                            .percentage(calcPercentage(generalTotal, goalTotal))
                            .routines(routineDtos)
                            .build();
                })
                .toList();

        return GoalCategoryTimeStatisticsDto.builder()
                .header(buildHeader(gc))
                .totalMinutes(goalTotal)
                .generalCategories(generalDtos)
                .build();
    }

    // ── ✅ 목표 상세 - "실천률" 탭 (달성률 + 연속달성 + 히트맵) ────

    public GoalCategoryAchievementStatisticsDto getGoalCategoryAchievementStatistics(
            Long userId, Long goalCategoryId, LocalDate startDate, LocalDate endDate) {

        GoalCategory gc = findOwnedGoalCategory(userId, goalCategoryId);
        LocalDateTime start = toKstDayStart(startDate);
        LocalDateTime end = toKstDayEnd(endDate);

        List<GeneralCategory> generalCategories = gc.getGeneralCategories().stream()
                .filter(g -> g.getGeneralCategoryStatus() != GoalCategoryStatus.DELETED)
                .toList();

        // 이 goalCategory 안의 routine 전체 + 완료날짜 + 개별 달성통계를 "한번씩만" 조회 (N+1 방지)
        List<Routine> allRoutines = routineRepository.findAllByGoalCategoryId(goalCategoryId);
        Map<Long, List<Routine>> routinesByGeneral = allRoutines.stream()
                .collect(Collectors.groupingBy(r -> r.getGeneralCategory().getGeneralCategoryId()));

        List<Long> routineIds = allRoutines.stream().map(Routine::getRoutineId).toList();
        Map<Long, List<LocalDate>> completedDatesByRoutineId = routineIds.isEmpty()
                ? Map.of()
                : taskRepository.findCompletedDatesGroupedByRoutineId(routineIds);

        Map<Long, RoutineAchievementStatProjection> routineStatMap =
                taskRepository.findRoutineAchievementStatsGroupedByRoutine(goalCategoryId, start, end).stream()
                        .collect(Collectors.toMap(RoutineAchievementStatProjection::getRoutineId, p -> p));

        List<GeneralCategoryAchievementDto> generalDtos = generalCategories.stream()
                .map(generalCategory -> buildGeneralAchievementDto(
                        generalCategory, start, end, routinesByGeneral, routineStatMap, completedDatesByRoutineId))
                .toList();

        return GoalCategoryAchievementStatisticsDto.builder()
                .header(buildHeader(gc))
                .generalCategories(generalDtos)
                .build();
    }

    private GeneralCategoryAchievementDto buildGeneralAchievementDto(
            GeneralCategory generalCategory,
            LocalDateTime start,
            LocalDateTime end,
            Map<Long, List<Routine>> routinesByGeneral,
            Map<Long, RoutineAchievementStatProjection> routineStatMap,
            Map<Long, List<LocalDate>> completedDatesByRoutineId
    ) {
        Long generalId = generalCategory.getGeneralCategoryId();

        AchievementStatProjection generalStat =
                taskRepository.findGeneralCategoryAchievementStats(generalId, start, end);

        List<Routine> routines = routinesByGeneral.getOrDefault(generalId, List.of());
        List<RoutineAchievementDto> routineDtos = routines.stream()
                .map(routine -> buildRoutineAchievementDto(routine, routineStatMap, completedDatesByRoutineId))
                .toList();

        long total = generalStat != null && generalStat.getTotalCount() != null ? generalStat.getTotalCount() : 0L;
        long achieved = generalStat != null && generalStat.getAchievedCount() != null ? generalStat.getAchievedCount() : 0L;

        return GeneralCategoryAchievementDto.builder()
                .generalCategoryId(generalId)
                .generalCategoryName(generalCategory.getGeneralCategoryName())
                .colorHex(generalCategory.getColor() != null ? generalCategory.getColor().getColorCode() : null)
                .achievementRate(calcPercentage(achieved, total))
                .achievedCount((int) achieved)
                .totalCount((int) total)
                .routines(routineDtos)
                .build();
    }

    private RoutineAchievementDto buildRoutineAchievementDto(
            Routine routine,
            Map<Long, RoutineAchievementStatProjection> routineStatMap,
            Map<Long, List<LocalDate>> completedDatesByRoutineId
    ) {
        Long routineId = routine.getRoutineId();
        RoutineAchievementStatProjection stat = routineStatMap.get(routineId);
        List<LocalDate> completedDates = completedDatesByRoutineId.getOrDefault(routineId, List.of());

        // ✅ 기존 RoutineStreakCalculator 그대로 재사용 — 루틴 상세 화면과 항상 같은 숫자 보장
        int currentStreak = RoutineStreakCalculator.calculateCurrentStreak(routine, completedDates);
        int longestStreak = RoutineStreakCalculator.calculateLongestStreak(routine, completedDates);

        long total = stat != null && stat.getTotalCount() != null ? stat.getTotalCount() : 0L;
        long achieved = stat != null && stat.getAchievedCount() != null ? stat.getAchievedCount() : 0L;

        return RoutineAchievementDto.builder()
                .routineId(routineId)
                .routineName(routine.getRoutineName())
                .achievementRate(calcPercentage(achieved, total))
                .achievedCount((int) achieved)
                .totalCount((int) total)
                .currentStreak(currentStreak)
                .longestStreak(longestStreak)
                .repeatDays(routine.getRepeatDays().stream().map(rd -> rd.getDayOfWeek().name()).toList())
                .completedDates(completedDates)
                .build();
    }

    // ── 공통 헬퍼 ────────────────────────────────────────

    private GoalCategory findOwnedGoalCategory(Long userId, Long goalCategoryId) {
        GoalCategory gc = goalCategoryRepository.findByIdWithAll(goalCategoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.GOAL_CATEGORY_NOT_FOUND));
        if (!gc.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        return gc;
    }

    private GoalCategoryHeaderDto buildHeader(GoalCategory gc) {
        boolean isCompleted = gc.getGoalCategoryStatus() == GoalCategoryStatus.COMPLETED;
        return GoalCategoryHeaderDto.builder()
                .goalCategoryId(gc.getGoalCategoryId())
                .goalCategoryName(gc.getGoalCategoryName())
                .status(gc.getGoalCategoryStatus())
                .startDate(gc.getGoalCategoryStartDate())
                .endDate(gc.getGoalCategoryEndDate())
                .motive(isCompleted ? null : gc.getMotive())
                .achievement(isCompleted ? gc.getAchievement() : null)
                .achievementImageUrl(isCompleted ? gc.getAchievementImageUrl() : null)
                .build();
    }

    private GoalCategoryStatisticsDto buildStatisticsDto(
            GoalCategory gc,
            Map<Long, Long> generalCountMap,
            Map<Long, Long> routineCountMap,
            Map<Long, Long> totalMinutesMap,
            Map<Long, GoalAchievementStatProjection> planStatMap,
            Map<Long, GoalAchievementStatProjection> routineStatMap,
            LocalDate today
    ) {
        Long goalCategoryId = gc.getGoalCategoryId();
        boolean isCompleted = gc.getGoalCategoryStatus() == GoalCategoryStatus.COMPLETED;

        LocalDate endForCalc = isCompleted ? gc.getGoalCategoryEndDate() : today;
        Long daysTogether = (gc.getGoalCategoryStartDate() != null && endForCalc != null)
                ? ChronoUnit.DAYS.between(gc.getGoalCategoryStartDate(), endForCalc)
                : null;

        GoalAchievementStatProjection planStat = planStatMap.get(goalCategoryId);
        GoalAchievementStatProjection routineStat = routineStatMap.get(goalCategoryId);

        return GoalCategoryStatisticsDto.builder()
                .goalCategoryId(goalCategoryId)
                .goalCategoryName(gc.getGoalCategoryName())
                .status(gc.getGoalCategoryStatus())
                .colorHex(gc.getUserColor() != null ? gc.getUserColor().getColorCode() : null)
                .generalCategoryCount(toIntSafe(generalCountMap.get(goalCategoryId)))
                .routineCount(toIntSafe(routineCountMap.get(goalCategoryId)))
                .startDate(gc.getGoalCategoryStartDate())
                .endDate(gc.getGoalCategoryEndDate())
                .daysTogether(daysTogether)
                .totalMinutes(totalMinutesMap.getOrDefault(goalCategoryId, 0L))
                .routineAchievementRate(calcRate(routineStat))
                .routineAchievedCount(toIntSafe(routineStat != null ? routineStat.getAchievedCount() : 0L))
                .routineTotalCount(toIntSafe(routineStat != null ? routineStat.getTotalCount() : 0L))
                .planAchievementRate(calcRate(planStat))
                .planAchievedCount(toIntSafe(planStat != null ? planStat.getAchievedCount() : 0L))
                .planTotalCount(toIntSafe(planStat != null ? planStat.getTotalCount() : 0L))
                .motive(isCompleted ? null : gc.getMotive())
                .achievement(isCompleted ? gc.getAchievement() : null)
                .achievementImageUrl(isCompleted ? gc.getAchievementImageUrl() : null)
                .build();
    }

    private Map<Long, Long> toCountMap(List<GoalCountProjection> projections) {
        return projections.stream()
                .collect(Collectors.toMap(GoalCountProjection::getGoalCategoryId, GoalCountProjection::getCount));
    }

    private Double calcRate(GoalAchievementStatProjection stat) {
        if (stat == null) return 0.0;
        long total = stat.getTotalCount() != null ? stat.getTotalCount() : 0L;
        long achieved = stat.getAchievedCount() != null ? stat.getAchievedCount() : 0L;
        return calcPercentage(achieved, total);
    }

    private Integer toIntSafe(Long value) {
        return value != null ? value.intValue() : 0;
    }

    // ⚠️ 목표별 breakdown을 만들면서 전체 합계도 같이 계산 (기존 toAchievementDto(AchievementStatProjection) 대체)
    private AchievementChartDto toAchievementDtoWithBreakdown(List<GoalAchievementStatProjection> grouped) {
        long total = grouped.stream()
                .mapToLong(p -> p.getTotalCount() != null ? p.getTotalCount() : 0L)
                .sum();
        long achieved = grouped.stream()
                .mapToLong(p -> p.getAchievedCount() != null ? p.getAchievedCount() : 0L)
                .sum();

        List<GoalAchievementBreakdownDto> breakdown = grouped.stream()
                .map(p -> GoalAchievementBreakdownDto.builder()
                        .goalCategoryId(p.getGoalCategoryId())
                        .status(GoalCategoryStatus.valueOf(p.getGoalCategoryStatus()))
                        .totalCount(p.getTotalCount() != null ? p.getTotalCount() : 0L)
                        .achievedCount(p.getAchievedCount() != null ? p.getAchievedCount() : 0L)
                        .build())
                .toList();

        return AchievementChartDto.builder()
                .totalCount(total)
                .achievedCount(achieved)
                .unachievedCount(total - achieved)
                .achievementRate(calcPercentage(achieved, total))
                .breakdown(breakdown)
                .build();
    }

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

        return ChartResponseDto.builder()
                .totalMinutes(total)
                .slices(slices)
                .build();
    }

    // ⚠️ "전체 목표" 도넛 전용 — 슬라이스마다 goalCategoryStatus를 실어서 내려줌
    private ChartResponseDto buildGoalChartResponse(List<GoalCategoryStatProjection> stats) {
        long total = stats.stream().mapToLong(GoalCategoryStatProjection::getTotalMinutes).sum();

        List<ChartSliceDto> slices = stats.stream()
                .map(p -> ChartSliceDto.builder()
                        .id(p.getCategoryId())
                        .name(p.getCategoryName())
                        .colorHex(p.getColorHex())
                        .totalMinutes(p.getTotalMinutes())
                        .percentage(calcPercentage(p.getTotalMinutes(), total))
                        .status(p.getGoalCategoryStatus())
                        .build())
                .toList();

        return ChartResponseDto.builder()
                .totalMinutes(total)
                .slices(slices)
                .build();
    }

    private double calcPercentage(long part, long total) {
        if (total == 0) return 0.0;
        return Math.round((double) part / total * 1000.0) / 10.0;
    }

    private LocalDateTime toKstDayStart(LocalDate date) {
        if (date == null) return null;
        return date.atStartOfDay().minusHours(KST_OFFSET_HOURS);
    }

    private LocalDateTime toKstDayEnd(LocalDate date) {
        if (date == null) return null;
        return date.plusDays(1).atStartOfDay().minusHours(KST_OFFSET_HOURS);
    }
}