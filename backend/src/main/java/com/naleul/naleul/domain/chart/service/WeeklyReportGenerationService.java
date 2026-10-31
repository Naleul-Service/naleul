package com.naleul.naleul.domain.chart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naleul.naleul.domain.chart.dto.*;
import com.naleul.naleul.domain.chart.repository.ChartRepository;
import com.naleul.naleul.domain.chart.repository.projection.GoalCategoryStatProjection;
import com.naleul.naleul.domain.chart.repository.projection.GoalWeeklyPlanRoutineStatProjection;
import com.naleul.naleul.domain.chart.repository.projection.GoalWeeklyStatProjection;
import com.naleul.naleul.domain.chart.client.AiFeedbackClient;
import com.naleul.naleul.domain.chart.dto.*;
import com.naleul.naleul.domain.chart.entity.WeeklyReport;
import com.naleul.naleul.domain.chart.repository.WeeklyReportRepository;
import com.naleul.naleul.domain.chart.repository.projection.HourlyStatProjection;
import com.naleul.naleul.domain.chart.repository.projection.RoutineDailyCompletionProjection;
import com.naleul.naleul.domain.chart.repository.projection.WeekdayCountProjection;
import com.naleul.naleul.domain.routineRepeatDay.entity.RoutineRepeatDay;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.enums.UserPatternType;
import com.naleul.naleul.domain.user.repository.UserRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.routineRepeatDay.entity.RoutineRepeatDay;
import java.time.DayOfWeek;
import java.util.Set;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WeeklyReportGenerationService {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ChartRepository chartRepository;
    private final WeeklyReportRepository weeklyReportRepository;
    private final AiFeedbackClient aiFeedbackClient;
    private final ObjectMapper objectMapper;
    private final RoutineRepository routineRepository;

    private static final int KST_OFFSET_HOURS = 9;
    private static final List<String> WEEKDAY_NAMES = List.of(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
    );

    @Transactional
    public void generateReport(Long userId, LocalDate weekStart, LocalDate weekEnd) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (weeklyReportRepository.existsByUserUserIdAndWeekStartDate(userId, weekStart)) {
            return; // 중복 생성 방지
        }

        LocalDateTime start = toKstDayStart(weekStart);
        LocalDateTime end = toKstDayEnd(weekEnd);

        // ── ② 시간 패턴 분석 ──
        TimePatternAnalysisDto timePattern = buildTimePatternAnalysis(userId, start, end);

        // ── ③ 루틴 달성률 ──
        RoutineAchievementWeeklyDto routineAchievement = buildRoutineAchievement(userId, weekStart, weekEnd);

        // ── ④ 목표별 시간 배분 ──
        // ⚠️ findGoalCategoryStats 반환 타입이 CategoryStatProjection → GoalCategoryStatProjection으로 변경됨
        List<GoalCategoryStatProjection> goalStats = chartRepository.findGoalCategoryStats(userId, start, end);
        ChartResponseDto goalTimeDistribution = buildChartResponse(goalStats);
        Map<Long, Long> goalMinutesMap = goalStats.stream()
                .collect(Collectors.toMap(GoalCategoryStatProjection::getCategoryId, GoalCategoryStatProjection::getTotalMinutes));

        // ── ⑤ 목표별 순위 ──
        List<GoalWeeklyStatProjection> thisWeekGoalStats = taskRepository.findGoalWeeklyAchievementStats(userId, start, end);
        GoalWeeklyRankingDto goalRanking = buildGoalRanking(thisWeekGoalStats);

        // ── ⑥ 지난주 대비 ──
        LocalDate prevWeekStart = weekStart.minusWeeks(1);
        LocalDate prevWeekEnd = weekEnd.minusWeeks(1);
        LocalDateTime prevStart = toKstDayStart(prevWeekStart);
        LocalDateTime prevEnd = toKstDayEnd(prevWeekEnd);

        List<GoalWeeklyStatProjection> lastWeekGoalStats = taskRepository.findGoalWeeklyAchievementStats(userId, prevStart, prevEnd);
        List<GoalCategoryStatProjection> lastWeekGoalTime = chartRepository.findGoalCategoryStats(userId, prevStart, prevEnd);
        WeekOverWeekDto weekOverWeek = buildWeekOverWeek(thisWeekGoalStats, goalMinutesMap, lastWeekGoalStats, lastWeekGoalTime);

        // ── ✅ 신규: 월간 리포트가 재사용할 "목표별 계획/루틴 분리 통계" + 전체 Task 개수 ──
        List<GoalWeeklyPlanRoutineStatProjection> planRoutineStats =
                taskRepository.findGoalWeeklyPlanRoutineStats(userId, start, end);
        List<GoalWeeklyBreakdownItemDto> goalWeeklyBreakdown =
                buildGoalWeeklyBreakdown(planRoutineStats, goalMinutesMap);
        int totalTaskCount = planRoutineStats.stream()
                .mapToInt(s -> {
                    long plan = s.getPlanTotalCount() != null ? s.getPlanTotalCount() : 0L;
                    long routine = s.getRoutineTotalCount() != null ? s.getRoutineTotalCount() : 0L;
                    return (int) (plan + routine);
                })
                .sum();

        // ── ① AI 피드백 (동기 호출) ──
        UserPatternType patternType = user.getPatternType() != null ? user.getPatternType() : UserPatternType.BALANCE;
        String statsSummary = buildStatsSummaryForPrompt(timePattern, goalRanking, weekOverWeek, routineAchievement, totalTaskCount);
        AiFeedbackDto aiFeedback = aiFeedbackClient.generateFeedback(patternType, statsSummary, "이번 주");

        // ── 전부 조립해서 한번에 저장 ──
        WeeklyReport report = WeeklyReport.builder()
                .user(user)
                .weekStartDate(weekStart)
                .weekEndDate(weekEnd)
                .patternTypeSnapshot(patternType)
                .aiFeedbackTitle(aiFeedback.getTitle())
                .aiFeedbackContent(aiFeedback.getContent())
                .timePatternJson(toJson(timePattern))
                .routineAchievementJson(toJson(routineAchievement))
                .goalTimeDistributionJson(toJson(goalTimeDistribution))
                .goalRankingJson(toJson(goalRanking))
                .weekOverWeekJson(toJson(weekOverWeek))
                .totalTaskCount(totalTaskCount)
                .goalWeeklyBreakdownJson(toJson(goalWeeklyBreakdown))
                .build();

        weeklyReportRepository.save(report);
    }

    // ── ② 시간 패턴 분석 ──────────────────────────────────

    private TimePatternAnalysisDto buildTimePatternAnalysis(Long userId, LocalDateTime start, LocalDateTime end) {
        List<HourlyStatProjection> stats = taskRepository.findHourlyPatternStats(userId, start, end);

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

    // ── ③ 루틴 달성률 ────────────────────────────────────

    private RoutineAchievementWeeklyDto buildRoutineAchievement(Long userId, LocalDate weekStart, LocalDate weekEnd) {
        List<WeekdayCountProjection> weekdayStats = taskRepository.findRoutineWeekdayCompletionCounts(userId, weekStart, weekEnd);
        Map<Integer, Long> countByWeekday = weekdayStats.stream()
                .collect(Collectors.toMap(WeekdayCountProjection::getWeekday, WeekdayCountProjection::getCount));

        List<WeekdayCountDto> lineChart = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            lineChart.add(WeekdayCountDto.builder()
                    .weekday(WEEKDAY_NAMES.get(i))
                    .count(countByWeekday.getOrDefault(i, 0L))
                    .build());
        }

        List<RoutineDailyCompletionProjection> completions = taskRepository.findRoutineDailyCompletionsInWeek(userId, weekStart, weekEnd);

        Map<Long, String> routineNames = new LinkedHashMap<>();
        Map<Long, boolean[]> completedMap = new HashMap<>();

        for (RoutineDailyCompletionProjection c : completions) {
            routineNames.putIfAbsent(c.getRoutineId(), c.getRoutineName());
            boolean[] cells = completedMap.computeIfAbsent(c.getRoutineId(), k -> new boolean[7]);
            int weekdayIndex = c.getRoutineDate().getDayOfWeek().getValue() - 1;
            if ("COMPLETED".equals(c.getTaskStatus())) {
                cells[weekdayIndex] = true;
            }
        }

        // ✅ Task 테이블이 아니라 Routine.repeatDays에서 "진짜 스케줄"을 가져옴
        List<Long> routineIds = new ArrayList<>(routineNames.keySet());
        Map<Long, Set<DayOfWeek>> repeatDaysMap = routineIds.isEmpty()
                ? Map.of()
                : routineRepository.findAllByRoutineIdInWithRepeatDays(routineIds).stream()
                  .collect(Collectors.toMap(
                          Routine::getRoutineId,
                          r -> r.getRepeatDays().stream()
                               .map(RoutineRepeatDay::getDayOfWeek)
                               .collect(Collectors.toSet())
                  ));

        List<RoutineHeatmapRowDto> heatmap = routineNames.entrySet().stream()
                .map(entry -> {
                    Long routineId = entry.getKey();
                    boolean[] completedCells = completedMap.getOrDefault(routineId, new boolean[7]);

                    // ✅ RoutineService의 생성 로직과 동일한 규칙: repeatDays가 비어있으면 "매일"
                    Set<DayOfWeek> repeatDays = repeatDaysMap.getOrDefault(routineId, Set.of());
                    List<Boolean> scheduledList = new ArrayList<>();
                    List<Boolean> completedList = new ArrayList<>();
                    for (int i = 0; i < 7; i++) {
                        DayOfWeek dow = DayOfWeek.of(i + 1); // 1=MONDAY ... 7=SUNDAY
                        boolean scheduled = repeatDays.isEmpty() || repeatDays.contains(dow);
                        scheduledList.add(scheduled);
                        completedList.add(completedCells[i]);
                    }

                    return RoutineHeatmapRowDto.builder()
                            .routineId(routineId)
                            .routineName(entry.getValue())
                            .completedByWeekday(completedList)
                            .scheduledByWeekday(scheduledList)
                            .build();
                })
                .toList();

        return RoutineAchievementWeeklyDto.builder()
                .lineChart(lineChart)
                .heatmap(heatmap)
                .build();
    }

    // ── ⑤ 목표별 순위 ────────────────────────────────────

    private GoalWeeklyRankingDto buildGoalRanking(List<GoalWeeklyStatProjection> stats) {
        if (stats.isEmpty()) {
            return GoalWeeklyRankingDto.builder().build();
        }

        List<GoalRankItemDto> items = stats.stream()
                .map(s -> {
                    long total = s.getTotalCount() != null ? s.getTotalCount() : 0L;
                    long achieved = s.getAchievedCount() != null ? s.getAchievedCount() : 0L;
                    return GoalRankItemDto.builder()
                            .goalCategoryId(s.getGoalCategoryId())
                            .goalCategoryName(s.getGoalCategoryName())
                            .achievedCount(achieved)
                            .totalCount(total)
                            .achievementRate(calcPercentage(achieved, total))
                            .build();
                })
                .toList();

        return GoalWeeklyRankingDto.builder()
                .mostAchievedGoal(items.stream().max(Comparator.comparingLong(GoalRankItemDto::getAchievedCount)).orElse(null))
                .leastAchievedGoal(items.stream().min(Comparator.comparingLong(GoalRankItemDto::getAchievedCount)).orElse(null))
                .highestRateGoal(items.stream().max(Comparator.comparingDouble(GoalRankItemDto::getAchievementRate)).orElse(null))
                .lowestRateGoal(items.stream().min(Comparator.comparingDouble(GoalRankItemDto::getAchievementRate)).orElse(null))
                .build();
    }

    // ── ⑥ 지난주 대비 ────────────────────────────────────

    private WeekOverWeekDto buildWeekOverWeek(
            List<GoalWeeklyStatProjection> thisWeekStats, Map<Long, Long> thisWeekMinutesMap,
            List<GoalWeeklyStatProjection> lastWeekStats, List<GoalCategoryStatProjection> lastWeekGoalTime
    ) {
        Map<Long, Long> lastWeekMinutesMap = lastWeekGoalTime.stream()
                .collect(Collectors.toMap(GoalCategoryStatProjection::getCategoryId, GoalCategoryStatProjection::getTotalMinutes));
        Map<Long, GoalWeeklyStatProjection> lastWeekMap = lastWeekStats.stream()
                .collect(Collectors.toMap(GoalWeeklyStatProjection::getGoalCategoryId, s -> s));

        Set<Long> allGoalIds = new HashSet<>();
        thisWeekStats.forEach(s -> allGoalIds.add(s.getGoalCategoryId()));
        lastWeekStats.forEach(s -> allGoalIds.add(s.getGoalCategoryId()));

        Map<Long, GoalWeeklyStatProjection> thisWeekMap = thisWeekStats.stream()
                .collect(Collectors.toMap(GoalWeeklyStatProjection::getGoalCategoryId, s -> s));

        List<WeekOverWeekGoalDto> comparisons = allGoalIds.stream()
                .map(goalId -> {
                    GoalWeeklyStatProjection thisWeek = thisWeekMap.get(goalId);
                    GoalWeeklyStatProjection lastWeek = lastWeekMap.get(goalId);

                    double thisRate = ratOf(thisWeek);
                    double lastRate = ratOf(lastWeek);
                    long thisMinutes = thisWeekMinutesMap.getOrDefault(goalId, 0L);
                    long lastMinutes = lastWeekMinutesMap.getOrDefault(goalId, 0L);

                    String name = thisWeek != null ? thisWeek.getGoalCategoryName()
                            : (lastWeek != null ? lastWeek.getGoalCategoryName() : "");

                    return WeekOverWeekGoalDto.builder()
                            .goalCategoryId(goalId)
                            .goalCategoryName(name)
                            .lastWeekRate(lastRate)
                            .thisWeekRate(thisRate)
                            .rateDeltaPercentPoint(Math.round((thisRate - lastRate) * 10.0) / 10.0)
                            .lastWeekMinutes(lastMinutes)
                            .thisWeekMinutes(thisMinutes)
                            .minutesDelta(thisMinutes - lastMinutes)
                            .build();
                })
                .toList();

        long totalThisWeekMinutes = thisWeekMinutesMap.values().stream().mapToLong(Long::longValue).sum();
        long totalLastWeekMinutes = lastWeekMinutesMap.values().stream().mapToLong(Long::longValue).sum();

        return WeekOverWeekDto.builder()
                .totalMinutesDelta(totalThisWeekMinutes - totalLastWeekMinutes)
                .goalComparisons(comparisons)
                .build();
    }

    private double ratOf(GoalWeeklyStatProjection s) {
        if (s == null) return 0.0;
        long total = s.getTotalCount() != null ? s.getTotalCount() : 0L;
        long achieved = s.getAchievedCount() != null ? s.getAchievedCount() : 0L;
        return calcPercentage(achieved, total);
    }

    // ── ✅ 신규: 목표별 계획/루틴 분리 주간 통계 (월간 리포트 재사용용) ────

    private List<GoalWeeklyBreakdownItemDto> buildGoalWeeklyBreakdown(
            List<GoalWeeklyPlanRoutineStatProjection> stats, Map<Long, Long> goalMinutesMap
    ) {
        return stats.stream()
                .map(s -> {
                    long planTotal = s.getPlanTotalCount() != null ? s.getPlanTotalCount() : 0L;
                    long planAchieved = s.getPlanAchievedCount() != null ? s.getPlanAchievedCount() : 0L;
                    long routineTotal = s.getRoutineTotalCount() != null ? s.getRoutineTotalCount() : 0L;
                    long routineAchieved = s.getRoutineAchievedCount() != null ? s.getRoutineAchievedCount() : 0L;

                    return GoalWeeklyBreakdownItemDto.builder()
                            .goalCategoryId(s.getGoalCategoryId())
                            .goalCategoryName(s.getGoalCategoryName())
                            .planAchievedCount(planAchieved)
                            .planTotalCount(planTotal)
                            .planAchievementRate(calcPercentage(planAchieved, planTotal))
                            .routineAchievedCount(routineAchieved)
                            .routineTotalCount(routineTotal)
                            .routineAchievementRate(calcPercentage(routineAchieved, routineTotal))
                            .totalMinutes(goalMinutesMap.getOrDefault(s.getGoalCategoryId(), 0L))
                            .build();
                })
                .toList();
    }

    // ── AI 프롬프트용 통계 요약 텍스트 생성 ──────────────────

    private String buildStatsSummaryForPrompt(
            TimePatternAnalysisDto timePattern,
            GoalWeeklyRankingDto ranking,
            WeekOverWeekDto weekOverWeek,
            RoutineAchievementWeeklyDto routineAchievement,
            int totalTaskCount
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("이번 주 전체 실천 Task 개수: %d개\n", totalTaskCount));

        if (timePattern.getBestHour() != null) {
            sb.append(String.format("가장 효율적인 시간대: %d시대 (완료율 %.1f%%, 전체 활동 중 %.1f%% 비중)\n",
                    timePattern.getBestHour(), timePattern.getBestRate(), timePattern.getBestRatioOfTotal()));
        }
        if (timePattern.getWorstHour() != null) {
            sb.append(String.format("가장 저조한 시간대: %d시대 (완료율 %.1f%%)\n",
                    timePattern.getWorstHour(), timePattern.getWorstRate()));
        }

        if (ranking.getHighestRateGoal() != null) {
            sb.append(String.format("실천률이 가장 높은 목표: %s (%.1f%%, %d/%d회 달성)\n",
                    ranking.getHighestRateGoal().getGoalCategoryName(),
                    ranking.getHighestRateGoal().getAchievementRate(),
                    ranking.getHighestRateGoal().getAchievedCount(),
                    ranking.getHighestRateGoal().getTotalCount()));
        }
        if (ranking.getLowestRateGoal() != null) {
            sb.append(String.format("실천률이 가장 낮은 목표: %s (%.1f%%, %d/%d회 달성)\n",
                    ranking.getLowestRateGoal().getGoalCategoryName(),
                    ranking.getLowestRateGoal().getAchievementRate(),
                    ranking.getLowestRateGoal().getAchievedCount(),
                    ranking.getLowestRateGoal().getTotalCount()));
        }
        if (ranking.getMostAchievedGoal() != null) {
            sb.append(String.format("가장 많이 실천한 목표: %s (%d회)\n",
                    ranking.getMostAchievedGoal().getGoalCategoryName(),
                    ranking.getMostAchievedGoal().getAchievedCount()));
        }

        if (weekOverWeek.getTotalMinutesDelta() != null) {
            sb.append(String.format("지난주 대비 총 투입 시간 변화: %+d분\n", weekOverWeek.getTotalMinutesDelta()));
        }
        if (weekOverWeek.getGoalComparisons() != null && !weekOverWeek.getGoalComparisons().isEmpty()) {
            weekOverWeek.getGoalComparisons().stream()
                    .max(Comparator.comparingLong(g -> Math.abs(g.getMinutesDelta())))
                    .ifPresent(g -> sb.append(String.format(
                            "지난주 대비 변화가 가장 큰 목표: %s (%+d분, 달성률 %+.1f%%p)\n",
                            g.getGoalCategoryName(), g.getMinutesDelta(), g.getRateDeltaPercentPoint())));
        }

        if (routineAchievement.getLineChart() != null && !routineAchievement.getLineChart().isEmpty()) {
            String weekdaySummary = routineAchievement.getLineChart().stream()
                    .filter(w -> w.getCount() > 0)
                    .map(w -> weekdayKoreanLabel(w.getWeekday()) + " " + w.getCount() + "회")
                    .collect(Collectors.joining(", "));
            if (!weekdaySummary.isEmpty()) {
                sb.append("요일별 루틴 완료 현황: ").append(weekdaySummary).append("\n");
            }
        }

        return sb.toString();
    }

    private String weekdayKoreanLabel(String weekday) {
        Map<String, String> map = Map.of(
                "MONDAY", "월", "TUESDAY", "화", "WEDNESDAY", "수", "THURSDAY", "목",
                "FRIDAY", "금", "SATURDAY", "토", "SUNDAY", "일"
        );
        return map.getOrDefault(weekday, weekday);
    }

    // ── 공통 유틸 ─────────────────────────────────────────

    // ⚠️ 파라미터 타입을 GoalCategoryStatProjection으로 변경
    private ChartResponseDto buildChartResponse(List<GoalCategoryStatProjection> stats) {
        long total = stats.stream().mapToLong(GoalCategoryStatProjection::getTotalMinutes).sum();
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

    private LocalDateTime toKstDayStart(LocalDate date) {
        return date.atStartOfDay().minusHours(KST_OFFSET_HOURS);
    }

    private LocalDateTime toKstDayEnd(LocalDate date) {
        return date.plusDays(1).atStartOfDay().minusHours(KST_OFFSET_HOURS);
    }
}