package com.naleul.naleul.domain.goalOutcome.service;

import com.naleul.naleul.domain.goalOutcome.entity.OutcomeSnapshot;
import com.naleul.naleul.domain.goalOutcome.enums.AutoReason;
import com.naleul.naleul.domain.goalOutcome.enums.OutcomeResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 목표가 끝난 시점의 스냅샷 · 자동 판정 (DB 없이 순수 계산).
 *
 * 자동 판정 (사용자가 고르지 않았을 때 — 기간이 끝나 자동 완료된 목표):
 *  - 수치 목표: 마지막 기록 기준 진행률 80% 이상 달성 · 60% 이상 부분 달성 · 그 밖 미달성
 *    (2026-10: 100% → 80%. 목표 수치에 충분히 다가갔으면 달성으로 본다 — 결과 확인 모달의 "80% 이상이라 자동 달성" 안내와 같은 기준)
 *  - 수치 없는 목표: 루틴 실천률(없으면 Task 실천률) 80% 이상 달성 · 50% 이상 부분 달성 · 그 밖 미달성
 *  - 수치 목표인데 수치를 기록하지 않았으면(metricUnrecorded) 수치 없는 목표처럼 실천률로 판정한다.
 *    현재값이 시작값 그대로라 진행률 0% → "미달성"이 되는데, 이건 실패가 아니라 "기록 안 함"이기 때문.
 */
public final class GoalOutcomeCalculator {

    static final int KEPT_RATE = 60;
    static final int KEPT_MIN_DUE = 4;
    /** 마지막 수치 기록이 끝난 날보다 이만큼 이전이면 "오래 기록 안 함" (단, 그때 이미 100% 면 달성으로 인정) */
    static final int STALE_METRIC_DAYS = 30;
    /** 이만큼 아무 활동이 없으면 "멈춤" — 목록 화면 표시(GoalPauseCalculator)와 같은 기준 */
    public static final int IDLE_PAUSE_DAYS = 14;
    /** 수치 목표 "달성" 기준 (마지막 기록 기준 진행률 %) */
    public static final int METRIC_ACHIEVED_PERCENT = 80;
    static final int METRIC_PARTIAL_PERCENT = 60;
    /** 수치 없는 목표 "달성" · "부분 달성" 기준 (루틴, 없으면 Task 실천률 %) */
    public static final int RATE_ACHIEVED_PERCENT = 80;
    static final int RATE_PARTIAL_PERCENT = 50;

    private GoalOutcomeCalculator() {
    }

    /** 해야 했던 루틴 하루치 (날짜가 오늘·끝낸 날 이후면 넣지 않는다) */
    public record RoutineDue(Long routineId, LocalDate date, boolean done) {
    }

    /** 일반 Task 하나 — plannedDate: 하기로 한 날, done: 완료했는지 */
    public record TaskDue(LocalDate plannedDate, boolean done) {
    }

    /**
     * 수치 기록 현황 — count: 끝난 날까지 기록한 횟수, lastDate: 마지막 기록일 (없으면 null)
     */
    public record MetricLogs(int count, LocalDate lastDate) {
        public static final MetricLogs NONE = new MetricLogs(0, null);
    }

    /** 수치 기록 · 활동 날짜를 모를 때 — 예전처럼 현재값을 그대로 믿는다 */
    public static OutcomeSnapshot snapshot(LocalDate start, LocalDate plannedEnd, LocalDate endedOn,
                                           BigDecimal metricStart, BigDecimal metricCurrent, BigDecimal metricTarget,
                                           List<RoutineDue> routineDues, List<TaskDue> tasks,
                                           Map<Long, Integer> routineWeeklyMinutes) {
        return snapshot(start, plannedEnd, endedOn, metricStart, metricCurrent, metricTarget,
                routineDues, tasks, routineWeeklyMinutes, null, null);
    }

    /**
     * @param metricLogs    수치 기록 현황 (null = 모름 → 현재값을 그대로 믿는다)
     * @param activityDates 활동한 날 (Task 완료 · 수치 기록). null = 모름 → longestIdleDays 도 null
     */
    public static OutcomeSnapshot snapshot(LocalDate start, LocalDate plannedEnd, LocalDate endedOn,
                                           BigDecimal metricStart, BigDecimal metricCurrent, BigDecimal metricTarget,
                                           List<RoutineDue> routineDues, List<TaskDue> tasks,
                                           Map<Long, Integer> routineWeeklyMinutes,
                                           MetricLogs metricLogs, Collection<LocalDate> activityDates) {
        Integer plannedDays = start != null && plannedEnd != null ? (int) ChronoUnit.DAYS.between(start, plannedEnd) : null;
        Integer actualDays = start != null && endedOn != null ? (int) Math.max(0, ChronoUnit.DAYS.between(start, endedOn)) : null;

        Integer metricPercent = null;
        if (metricStart != null && metricTarget != null && metricTarget.compareTo(metricStart) != 0) {
            BigDecimal current = metricCurrent != null ? metricCurrent : metricStart;
            metricPercent = current.subtract(metricStart).multiply(BigDecimal.valueOf(100))
                    .divide(metricTarget.subtract(metricStart), 0, RoundingMode.HALF_UP).intValue();
        }
        boolean metricUnrecorded = metricPercent != null && isMetricUnrecorded(metricLogs, endedOn, metricPercent);
        if (metricUnrecorded) metricPercent = null;   // 기록 안 한 수치는 판정·집계에 쓰지 않는다

        Integer routineRate = rate(routineDues.stream().filter(RoutineDue::done).count(), routineDues.size());
        List<TaskDue> due = tasks.stream()
                .filter(t -> t.done() || (t.plannedDate() != null && endedOn != null && !t.plannedDate().isAfter(endedOn)))
                .toList();
        Integer taskRate = rate(due.stream().filter(TaskDue::done).count(), due.size());

        Map<Long, List<RoutineDue>> byRoutine = routineDues.stream()
                .collect(Collectors.groupingBy(RoutineDue::routineId));
        int kept = (int) byRoutine.values().stream()
                .filter(list -> list.size() >= KEPT_MIN_DUE)
                .filter(list -> 100 * list.stream().filter(RoutineDue::done).count() / list.size() >= KEPT_RATE)
                .count();

        return new OutcomeSnapshot(plannedDays, actualDays, metricPercent, routineRate, taskRate,
                routineWeeklyMinutes.size(), kept,
                routineWeeklyMinutes.values().stream().mapToInt(Integer::intValue).sum(),
                metricUnrecorded,
                activityDates == null ? null : longestIdleDays(start, endedOn, activityDates));
    }

    /**
     * 수치를 "기록 안 한" 것으로 볼지.
     *  - 기록이 한 번도 없으면 → 기록 안 함
     *  - 마지막 기록이 끝난 날보다 STALE_METRIC_DAYS 이상 전이고 그때 달성 기준(80%) 미만이면 → 기록 안 함
     *    (일찍 달성하고 기록을 멈춘 사람은 달성으로 인정 — 달성 기준 80% 이상이면)
     */
    static boolean isMetricUnrecorded(MetricLogs logs, LocalDate endedOn, int metricPercent) {
        if (logs == null) return false;
        if (logs.count() == 0 || logs.lastDate() == null) return true;
        if (metricPercent >= METRIC_ACHIEVED_PERCENT || endedOn == null) return false;
        return ChronoUnit.DAYS.between(logs.lastDate(), endedOn) >= STALE_METRIC_DAYS;
    }

    /**
     * 시작일 ~ 끝난 날 사이에서 활동이 없던 가장 긴 구간 (일).
     * 예) 7/1 시작 · 7/3, 7/20 활동 · 7/31 끝 → 7/3~7/20 = 17일
     */
    public static Integer longestIdleDays(LocalDate start, LocalDate end, Collection<LocalDate> activityDates) {
        if (start == null || end == null || end.isBefore(start)) return null;
        List<LocalDate> days = activityDates.stream()
                .filter(d -> d != null && !d.isBefore(start) && !d.isAfter(end))
                .distinct().sorted().toList();
        long longest = 0;
        LocalDate prev = start;
        for (LocalDate d : days) {
            longest = Math.max(longest, ChronoUnit.DAYS.between(prev, d));
            prev = d;
        }
        longest = Math.max(longest, ChronoUnit.DAYS.between(prev, end));
        return (int) longest;
    }

    public static OutcomeResult infer(OutcomeSnapshot s) {
        if (s.metricPercent() != null) {
            return s.metricPercent() >= METRIC_ACHIEVED_PERCENT ? OutcomeResult.ACHIEVED
                    : s.metricPercent() >= METRIC_PARTIAL_PERCENT ? OutcomeResult.PARTIAL : OutcomeResult.NOT_ACHIEVED;
        }
        Integer r = s.routineRate() != null ? s.routineRate() : s.taskRate();
        if (r == null) return OutcomeResult.NOT_ACHIEVED;
        return r >= RATE_ACHIEVED_PERCENT ? OutcomeResult.ACHIEVED
                : r >= RATE_PARTIAL_PERCENT ? OutcomeResult.PARTIAL : OutcomeResult.NOT_ACHIEVED;
    }

    /** 자동 판정에 무엇을 썼는지 (infer 와 같은 우선순위) */
    public static AutoReason reasonOf(OutcomeSnapshot s) {
        if (s.metricPercent() != null) return AutoReason.METRIC;
        if (s.routineRate() != null) return AutoReason.ROUTINE_RATE;
        if (s.taskRate() != null) return AutoReason.TASK_RATE;
        return AutoReason.NO_RECORD;
    }

    /**
     * 자동 판정 안내 문구 (결과 확인 모달 · 목표 상세). 세 앱이 같은 문장을 쓰도록 서버에서 만든다.
     * 예) "마지막 기록이 목표 수치의 86%예요. 80% 이상이라 자동으로 달성 처리했어요."
     */
    public static String autoMessage(OutcomeResult result, AutoReason reason,
                                     Integer metricPercent, Integer routineRate, Integer taskRate) {
        if (result == null || reason == null) return null;
        return switch (reason) {
            case METRIC -> {
                if (metricPercent == null) yield null;
                String head = "마지막 기록이 목표 수치의 " + metricPercent + "%예요. ";
                yield switch (result) {
                    case ACHIEVED -> head + METRIC_ACHIEVED_PERCENT + "% 이상이라 자동으로 달성 처리했어요.";
                    case PARTIAL -> head + METRIC_PARTIAL_PERCENT + "% 이상이라 부분 달성으로 판정했어요.";
                    case NOT_ACHIEVED -> head + METRIC_PARTIAL_PERCENT + "%에 못 미쳐 미달성으로 판정했어요.";
                };
            }
            case ROUTINE_RATE, TASK_RATE -> {
                String what = reason == AutoReason.ROUTINE_RATE ? "루틴" : "할 일";
                Integer rate = reason == AutoReason.ROUTINE_RATE ? routineRate : taskRate;
                if (rate == null) yield null;
                String head = what + "을 " + rate + "% 실천했어요. ";
                yield switch (result) {
                    case ACHIEVED -> head + RATE_ACHIEVED_PERCENT + "% 이상이라 자동으로 달성 처리했어요.";
                    case PARTIAL -> head + RATE_PARTIAL_PERCENT + "% 이상이라 부분 달성으로 판정했어요.";
                    case NOT_ACHIEVED -> head + RATE_PARTIAL_PERCENT + "%에 못 미쳐 미달성으로 판정했어요.";
                };
            }
            case NO_RECORD -> "기간 동안 남긴 기록이 없어 미달성으로 판정했어요.";
        };
    }

    private static Integer rate(long done, int total) {
        return total == 0 ? null : (int) Math.round(100.0 * done / total);
    }
}
