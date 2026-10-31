package com.naleul.naleul.domain.chart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naleul.naleul.domain.chart.dto.ChartResponseDto;
import com.naleul.naleul.domain.chart.dto.ChartSliceDto;
import com.naleul.naleul.domain.chart.dto.GoalWeeklyBreakdownItemDto;
import com.naleul.naleul.domain.chart.client.AiFeedbackClient;
import com.naleul.naleul.domain.chart.dto.*;
import com.naleul.naleul.domain.chart.entity.MonthlyReport;
import com.naleul.naleul.domain.chart.entity.WeeklyReport;
import com.naleul.naleul.domain.chart.repository.MonthlyReportRepository;
import com.naleul.naleul.domain.chart.repository.WeeklyReportRepository;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.routine.util.RoutineStreakCalculator;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.enums.UserPatternType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyReportGenerationService {

    private final WeeklyReportRepository weeklyReportRepository;
    private final MonthlyReportRepository monthlyReportRepository;
    private final RoutineRepository routineRepository;
    private final AiFeedbackClient aiFeedbackClient;
    private final ObjectMapper objectMapper;

    private static final double CV_THRESHOLD = 0.3;      // 편차 "크다" 기준 (30%)
    private static final double LOW_RATE_THRESHOLD = 30.0; // 실천률 "낮다" 기준 (30%)
    private static final List<String> WEEKDAY_NAMES = List.of(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
    );

    // ── 주간 리포트가 4의 배수로 쌓일 때마다 월간 리포트 생성 (밀렸으면 한번에 따라잡음) ──
    // ⚠️ 스케줄러 전용. 온디맨드(사용자 클릭) 흐름에서는 아래 generateNextDueReport를 사용.

    @Transactional
    public void generateDueReports(Long userId) {
        List<WeeklyReport> allWeekly = weeklyReportRepository.findAllByUserUserIdOrderByWeekStartDateAsc(userId);
        long processedWeeks = monthlyReportRepository.countByUserUserId(userId) * 4;

        while (allWeekly.size() >= processedWeeks + 4) {
            List<WeeklyReport> targetWeeks = allWeekly.subList((int) processedWeeks, (int) processedWeeks + 4);
            generateFromWeeks(targetWeeks);
            processedWeeks += 4;
        }
    }

    // ── ✅ 신규: 온디맨드 — 가장 오래된 미처리 4주 묶음 "딱 하나만" 생성 ──
    // 반환값: 생성(혹은 이미 존재)된 리포트의 periodStartDate. 아직 4주가 안 채워졌으면 null.

    @Transactional
    public LocalDate generateNextDueReport(Long userId) {
        List<WeeklyReport> allWeekly = weeklyReportRepository.findAllByUserUserIdOrderByWeekStartDateAsc(userId);
        long processedWeeks = monthlyReportRepository.countByUserUserId(userId) * 4;

        if (allWeekly.size() < processedWeeks + 4) {
            return null;
        }

        List<WeeklyReport> targetWeeks = allWeekly.subList((int) processedWeeks, (int) processedWeeks + 4);
        LocalDate periodStart = targetWeeks.get(0).getWeekStartDate();

        generateFromWeeks(targetWeeks); // 이미 존재하면 내부에서 조용히 스킵됨

        return periodStart;
    }

    private void generateFromWeeks(List<WeeklyReport> weeks) {
        WeeklyReport first = weeks.get(0);
        WeeklyReport last = weeks.get(3);
        User user = first.getUser();
        LocalDate periodStart = first.getWeekStartDate();
        LocalDate periodEnd = last.getWeekEndDate();

        if (monthlyReportRepository.existsByUserUserIdAndPeriodStartDate(user.getUserId(), periodStart)) {
            return;
        }

        // ② 시간 패턴 — 4주치 병합
        TimePatternAnalysisDto timePattern = mergeTimePattern(weeks);

        // ③ 기간 패턴 — 주차별 총 Task 개수
        List<WeeklyTaskCountDto> weeklyTaskCounts = buildWeeklyTaskCounts(weeks);

        // ④ 루틴 패턴 — 요일별 합계
        List<WeekdayCountDto> routineWeekday = mergeRoutineWeekday(weeks);

        // ⑤ 루틴 순위 + 연속달성
        RoutineRankingDto routineRanking = buildRoutineRanking(weeks, periodStart, periodEnd);

        // ⑦ 목표별 시간 배분 — 월간 합계
        ChartResponseDto goalTimeDistribution = mergeGoalTimeDistribution(weeks);

        // ⑧ 목표별 주차별 시리즈 (계획/루틴 달성률 + 시간)
        List<GoalWeeklySeriesItemDto> goalWeeklySeries = buildGoalWeeklySeries(weeks);

        // ⑥ 편차 — 시간 기준 CV
        List<GoalDeviationItemDto> deviations = buildDeviations(goalWeeklySeries);

        // ① AI 피드백 (기존 AiFeedbackClient 그대로 재사용)
        UserPatternType patternType = user.getPatternType() != null ? user.getPatternType() : UserPatternType.BALANCE;
        // ⚠️ 재료를 4개(시간대/루틴/꾸준함/몰아하기)만 넘기던 걸 weeklyTaskCounts까지 추가해서 풍부하게 함
        //    (주간 리포트에서 했던 것과 동일한 이유 — 재료가 적으면 AI가 뭉뚱그린 답변만 낼 수밖에 없음)
        String summary = buildMonthlySummaryForPrompt(timePattern, routineRanking, deviations, weeklyTaskCounts);
        // ⚠️ periodLabel을 "이번 달"로 명시. 예전엔 클라이언트 안에 "이번 주"가 하드코딩돼 있어서
        //    월간 리포트에도 "이번 주 통계입니다"라고 잘못 전달되고 있었음.
        AiFeedbackDto aiFeedback = aiFeedbackClient.generateFeedback(patternType, summary, "이번 달");

        MonthlyReport report = MonthlyReport.builder()
                .user(user)
                .periodStartDate(periodStart)
                .periodEndDate(periodEnd)
                .patternTypeSnapshot(patternType)
                .aiFeedbackTitle(aiFeedback.getTitle())
                .aiFeedbackContent(aiFeedback.getContent())
                .timePatternJson(toJson(timePattern))
                .weeklyTaskCountJson(toJson(weeklyTaskCounts))
                .routineWeekdayJson(toJson(routineWeekday))
                .routineRankingJson(toJson(routineRanking))
                .goalTimeDistributionJson(toJson(goalTimeDistribution))
                .goalDeviationJson(toJson(deviations))
                .goalWeeklySeriesJson(toJson(goalWeeklySeries))
                .build();

        monthlyReportRepository.save(report);
        log.info("월간 리포트 생성 완료: userId={}, period={}~{}", user.getUserId(), periodStart, periodEnd);
    }

    // ── ② 시간 패턴: 4주치 hourlyBreakdown을 시간대별로 합산 ──────────

    private TimePatternAnalysisDto mergeTimePattern(List<WeeklyReport> weeks) {
        Map<Integer, long[]> merged = new HashMap<>(); // hour -> [totalCount, achievedCount]

        for (WeeklyReport w : weeks) {
            TimePatternAnalysisDto dto = fromJson(w.getTimePatternJson(), TimePatternAnalysisDto.class);
            if (dto == null || dto.getHourlyBreakdown() == null) continue;
            for (HourlyPatternDto h : dto.getHourlyBreakdown()) {
                long[] acc = merged.computeIfAbsent(h.getHour(), k -> new long[2]);
                acc[0] += h.getTotalCount();
                acc[1] += h.getAchievedCount();
            }
        }

        if (merged.isEmpty()) {
            return TimePatternAnalysisDto.builder().hourlyBreakdown(List.of()).build();
        }

        long totalAllHours = merged.values().stream().mapToLong(a -> a[0]).sum();

        List<HourlyPatternDto> breakdown = merged.entrySet().stream()
                .map(e -> HourlyPatternDto.builder()
                        .hour(e.getKey())
                        .totalCount(e.getValue()[0])
                        .achievedCount(e.getValue()[1])
                        .achievementRate(calcPercentage(e.getValue()[1], e.getValue()[0]))
                        .build())
                .sorted(Comparator.comparingInt(HourlyPatternDto::getHour))
                .toList();

        HourlyPatternDto best = breakdown.stream().max(Comparator.comparingDouble(HourlyPatternDto::getAchievementRate)).orElse(null);
        HourlyPatternDto worst = breakdown.stream().min(Comparator.comparingDouble(HourlyPatternDto::getAchievementRate)).orElse(null);

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

    // ── ③ 기간 패턴: 주차별 총 Task 개수 ──────────────────────

    private List<WeeklyTaskCountDto> buildWeeklyTaskCounts(List<WeeklyReport> weeks) {
        List<WeeklyTaskCountDto> result = new ArrayList<>();
        for (int i = 0; i < weeks.size(); i++) {
            WeeklyReport w = weeks.get(i);
            result.add(WeeklyTaskCountDto.builder()
                    .weekIndex(i + 1)
                    .weekStartDate(w.getWeekStartDate())
                    .weekEndDate(w.getWeekEndDate())
                    .totalTaskCount(w.getTotalTaskCount())
                    .build());
        }
        return result;
    }

    // ── ④ 루틴 패턴: 요일별 합계 ────────────────────────────

    private List<WeekdayCountDto> mergeRoutineWeekday(List<WeeklyReport> weeks) {
        Map<String, Long> totals = new LinkedHashMap<>();
        WEEKDAY_NAMES.forEach(day -> totals.put(day, 0L));

        for (WeeklyReport w : weeks) {
            RoutineAchievementWeeklyDto dto = fromJson(w.getRoutineAchievementJson(), RoutineAchievementWeeklyDto.class);
            if (dto == null || dto.getLineChart() == null) continue;
            for (WeekdayCountDto d : dto.getLineChart()) {
                totals.merge(d.getWeekday(), d.getCount(), Long::sum);
            }
        }

        return totals.entrySet().stream()
                .map(e -> WeekdayCountDto.builder().weekday(e.getKey()).count(e.getValue()).build())
                .toList();
    }

    // ── ⑤ 루틴 순위 + 연속달성 ────────────────────────────

    private RoutineRankingDto buildRoutineRanking(List<WeeklyReport> weeks, LocalDate periodStart, LocalDate periodEnd) {
        Map<Long, String> routineNames = new LinkedHashMap<>();
        Map<Long, List<LocalDate>> completedDatesByRoutine = new HashMap<>();
        Map<Long, Long> executedCountByRoutine = new HashMap<>();

        // ✅ 히트맵 boolean 배열 + 그 주의 시작일(weekStartDate)을 조합해서 "실제 날짜"를 복원함
        for (WeeklyReport w : weeks) {
            RoutineAchievementWeeklyDto dto = fromJson(w.getRoutineAchievementJson(), RoutineAchievementWeeklyDto.class);
            if (dto == null || dto.getHeatmap() == null) continue;
            for (RoutineHeatmapRowDto row : dto.getHeatmap()) {
                routineNames.putIfAbsent(row.getRoutineId(), row.getRoutineName());
                List<LocalDate> dates = completedDatesByRoutine.computeIfAbsent(row.getRoutineId(), k -> new ArrayList<>());
                List<Boolean> flags = row.getCompletedByWeekday();
                for (int i = 0; i < flags.size(); i++) {
                    if (Boolean.TRUE.equals(flags.get(i))) {
                        dates.add(w.getWeekStartDate().plusDays(i));
                        executedCountByRoutine.merge(row.getRoutineId(), 1L, Long::sum);
                    }
                }
            }
        }

        if (routineNames.isEmpty()) {
            return RoutineRankingDto.builder().build();
        }

        // repeatDays 정보는 JSON에 없어서, 루틴 메타데이터만 가볍게 다시 조회
        Map<Long, Routine> routineMap = routineRepository.findAllById(routineNames.keySet()).stream()
                .collect(Collectors.toMap(Routine::getRoutineId, r -> r));

        List<RoutineRankItemDto> items = routineNames.entrySet().stream()
                .map(entry -> {
                    Long routineId = entry.getKey();
                    Routine routine = routineMap.get(routineId);
                    int longestStreak = routine != null
                            ? RoutineStreakCalculator.calculateLongestStreakInRange(
                            routine, completedDatesByRoutine.getOrDefault(routineId, List.of()), periodStart, periodEnd)
                            : 0;
                    return RoutineRankItemDto.builder()
                            .routineId(routineId)
                            .routineName(entry.getValue())
                            .executedCount(executedCountByRoutine.getOrDefault(routineId, 0L))
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

    // ── ⑦ 목표별 시간 배분: 4주치 도넛차트 병합 ──────────────

    private ChartResponseDto mergeGoalTimeDistribution(List<WeeklyReport> weeks) {
        Map<Long, ChartSliceDto> firstSeen = new LinkedHashMap<>();
        Map<Long, Long> minutesById = new HashMap<>();

        for (WeeklyReport w : weeks) {
            ChartResponseDto dto = fromJson(w.getGoalTimeDistributionJson(), ChartResponseDto.class);
            if (dto == null || dto.getSlices() == null) continue;
            for (ChartSliceDto slice : dto.getSlices()) {
                firstSeen.putIfAbsent(slice.getId(), slice);
                minutesById.merge(slice.getId(), slice.getTotalMinutes(), Long::sum);
            }
        }

        long total = minutesById.values().stream().mapToLong(Long::longValue).sum();

        List<ChartSliceDto> slices = firstSeen.values().stream()
                .map(s -> ChartSliceDto.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .colorHex(s.getColorHex())
                        .totalMinutes(minutesById.get(s.getId()))
                        .percentage(calcPercentage(minutesById.get(s.getId()), total))
                        .build())
                .toList();

        return ChartResponseDto.builder().totalMinutes(total).slices(slices).build();
    }

    // ── ⑧ 목표별 주차별 시리즈 (계획/루틴 달성률 + 시간) ────────

    private List<GoalWeeklySeriesItemDto> buildGoalWeeklySeries(List<WeeklyReport> weeks) {
        Map<Long, String> goalNames = new LinkedHashMap<>();
        List<Map<Long, GoalWeeklyBreakdownItemDto>> perWeekMaps = new ArrayList<>();

        for (WeeklyReport w : weeks) {
            List<GoalWeeklyBreakdownItemDto> items = fromJsonList(w.getGoalWeeklyBreakdownJson(), GoalWeeklyBreakdownItemDto.class);
            Map<Long, GoalWeeklyBreakdownItemDto> map = items.stream()
                    .collect(Collectors.toMap(GoalWeeklyBreakdownItemDto::getGoalCategoryId, i -> i));
            items.forEach(i -> goalNames.putIfAbsent(i.getGoalCategoryId(), i.getGoalCategoryName()));
            perWeekMaps.add(map);
        }

        return goalNames.entrySet().stream()
                .map(entry -> {
                    Long goalId = entry.getKey();
                    List<Double> planRates = new ArrayList<>();
                    List<Double> routineRates = new ArrayList<>();
                    List<Long> minutes = new ArrayList<>();

                    for (Map<Long, GoalWeeklyBreakdownItemDto> weekMap : perWeekMaps) {
                        GoalWeeklyBreakdownItemDto item = weekMap.get(goalId);
                        planRates.add(item != null ? item.getPlanAchievementRate() : 0.0);
                        routineRates.add(item != null ? item.getRoutineAchievementRate() : 0.0);
                        minutes.add(item != null ? item.getTotalMinutes() : 0L);
                    }

                    return GoalWeeklySeriesItemDto.builder()
                            .goalCategoryId(goalId)
                            .goalCategoryName(entry.getValue())
                            .weeklyPlanRates(planRates)
                            .weeklyRoutineRates(routineRates)
                            .weeklyMinutes(minutes)
                            .build();
                })
                .toList();
    }

    // ── ⑥ 편차: 시간(분) 기준 CV ──────────────────────────

    private List<GoalDeviationItemDto> buildDeviations(List<GoalWeeklySeriesItemDto> series) {
        return series.stream()
                .map(s -> {
                    double[] values = s.getWeeklyMinutes().stream().mapToDouble(Long::doubleValue).toArray();
                    double mean = Arrays.stream(values).average().orElse(0.0);
                    double cv = calcCV(values, mean);
                    return GoalDeviationItemDto.builder()
                            .goalCategoryId(s.getGoalCategoryId())
                            .goalCategoryName(s.getGoalCategoryName())
                            .weeklyMinutes(s.getWeeklyMinutes())
                            .averageMinutes(Math.round(mean))
                            .coefficientOfVariation(Math.round(cv * 1000.0) / 1000.0)
                            .classification(cv <= CV_THRESHOLD ? "CONSISTENT" : "BURST")
                            .build();
                })
                .toList();
    }

    private double calcCV(double[] values, double mean) {
        if (mean == 0) return 0.0;
        double variance = Arrays.stream(values).map(v -> Math.pow(v - mean, 2)).average().orElse(0.0);
        return Math.sqrt(variance) / mean;
    }

    // ── AI 프롬프트용 요약 ────────────────────────────────
    // ⚠️ 기존엔 4줄(시간대/루틴 1개/꾸준한 목표/몰아서 하는 목표)만 넘겨서 재료가 너무 적었음.
    //    주간 리포트 때와 동일한 이유로, worst hour·최다/최소 실행 루틴·연속달성·주차별 Task 추이·
    //    실제 CV 수치·평균 시간까지 넣어서 AI가 구체적으로 쓸 재료를 늘림.
    private String buildMonthlySummaryForPrompt(
            TimePatternAnalysisDto timePattern,
            RoutineRankingDto routineRanking,
            List<GoalDeviationItemDto> deviations,
            List<WeeklyTaskCountDto> weeklyTaskCounts
    ) {
        StringBuilder sb = new StringBuilder();

        if (weeklyTaskCounts != null && !weeklyTaskCounts.isEmpty()) {
            String weeklyTrend = weeklyTaskCounts.stream()
                    .map(w -> (w.getWeekIndex() + "주차 " + w.getTotalTaskCount() + "개"))
                    .collect(Collectors.joining(", "));
            sb.append("주차별 전체 실천 Task 추이: ").append(weeklyTrend).append("\n");
        }

        if (timePattern.getBestHour() != null) {
            sb.append(String.format("한 달 통틀어 가장 효율적인 시간대: %d시대 (완료율 %.1f%%)\n",
                    timePattern.getBestHour(), timePattern.getBestRate()));
        }
        if (timePattern.getWorstHour() != null) {
            sb.append(String.format("한 달 통틀어 가장 저조한 시간대: %d시대 (완료율 %.1f%%)\n",
                    timePattern.getWorstHour(), timePattern.getWorstRate()));
        }

        if (routineRanking.getMostExecutedRoutine() != null) {
            sb.append(String.format("가장 많이 실행한 루틴: %s (%d회)\n",
                    routineRanking.getMostExecutedRoutine().getRoutineName(),
                    routineRanking.getMostExecutedRoutine().getExecutedCount()));
        }
        if (routineRanking.getLeastExecutedRoutine() != null) {
            sb.append(String.format("가장 적게 실행한 루틴: %s (%d회)\n",
                    routineRanking.getLeastExecutedRoutine().getRoutineName(),
                    routineRanking.getLeastExecutedRoutine().getExecutedCount()));
        }
        if (routineRanking.getHighestStreakRoutine() != null) {
            sb.append(String.format("가장 연속달성이 긴 루틴: %s (%d일 연속)\n",
                    routineRanking.getHighestStreakRoutine().getRoutineName(),
                    routineRanking.getHighestStreakRoutine().getLongestStreak()));
        }

        deviations.stream().min(Comparator.comparingDouble(GoalDeviationItemDto::getCoefficientOfVariation))
                .ifPresent(d -> sb.append(String.format(
                        "가장 꾸준한 목표: %s (편차지수 %.2f, 주당 평균 %d분)\n",
                        d.getGoalCategoryName(), d.getCoefficientOfVariation(), d.getAverageMinutes())));
        deviations.stream().max(Comparator.comparingDouble(GoalDeviationItemDto::getCoefficientOfVariation))
                .ifPresent(d -> sb.append(String.format(
                        "가장 몰아서 하는(들쭉날쭉한) 목표: %s (편차지수 %.2f, 주당 평균 %d분)\n",
                        d.getGoalCategoryName(), d.getCoefficientOfVariation(), d.getAverageMinutes())));

        return sb.toString();
    }

    // ── 공통 유틸 ─────────────────────────────────────────

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