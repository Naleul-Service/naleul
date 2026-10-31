package com.naleul.naleul.domain.pattern.calculator;

import com.naleul.naleul.domain.pattern.entity.TaskScheduleLog;
import com.naleul.naleul.domain.pattern.enums.TaskScheduleAction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** 요약 4칸 계산 (명세 4 · 2-1) */
public final class SummaryCalculator {

    static final int MIN_EVALUATED = 10;
    static final int MIN_WEEK_SAMPLES = 5;
    static final double STREAK_DAY_RATE = 0.5;
    static final int MIN_DELAY_SAMPLES = 5;
    static final int DELAY_MIN = -120;
    static final int DELAY_MAX = 180;
    static final int MIN_ENGINE_BLOCKS = 5;

    private SummaryCalculator() {
    }

    public static SummaryStats calculate(PatternSampleSet set) {
        List<String> insufficient = new ArrayList<>();

        // 1) 전체 실행률 = 완료 / (평가 대상 + 이월 놓침 + 옛 이월 횟수)
        int evaluated = set.tasks().size();
        long done = set.tasks().stream().filter(PatternTaskSample::completed).count();
        long carry = set.carryMisses().stream().filter(m -> set.range().contains(m.date())).count();
        int denominator = evaluated + (int) carry + set.legacyCarryMisses();
        Integer rate = null;
        if (evaluated >= MIN_EVALUATED && denominator > 0) {
            rate = Samples.round(100.0 * done / denominator);
        } else {
            insufficient.add("executionRate");
        }

        // 2) 첫 주 대비
        Integer delta = firstWeekDelta(set);
        if (delta == null) insufficient.add("firstWeekDelta");

        // 3) 연속 실행
        int[] streaks = streaks(set);

        // 4) 시작 지연
        Integer avg = avgDelay(set.tasks(), 0, 24);
        Integer morning = avgDelay(set.tasks(), 5, 12);
        Integer evening = avgDelay(set.tasks(), 18, 24);
        if (avg == null) insufficient.add("avgStartDelayMinutes");

        // 5) AI 배치 수정률
        int[] engine = engineEdits(set);
        Integer editRate = null, keptPerTen = null;
        if (engine[0] >= MIN_ENGINE_BLOCKS) {
            editRate = Samples.round(100.0 * engine[1] / engine[0]);
            keptPerTen = Samples.round(10.0 * (engine[0] - engine[1]) / engine[0]);
        } else {
            insufficient.add("aiPlacementEditRate");
        }

        return new SummaryStats(rate, delta, streaks[0], streaks[1], avg, morning, evening,
                editRate, keptPerTen, engine[0], List.copyOf(insufficient));
    }

    /** 마지막 주 실행률 − 첫 주 실행률 (%p). 주가 2개 미만이거나 표본이 적으면 null */
    static Integer firstWeekDelta(PatternSampleSet set) {
        List<RateCounter> weeks = new ArrayList<>(Samples.byWeek(set).values());
        if (weeks.size() < 2) return null;
        RateCounter first = weeks.get(0), last = weeks.get(weeks.size() - 1);
        if (first.total() < MIN_WEEK_SAMPLES || last.total() < MIN_WEEK_SAMPLES) return null;
        return Samples.round(100 * last.ratio()) - Samples.round(100 * first.ratio());
    }

    /**
     * [최장, 현재] 연속 실행 일수.
     * 실행한 날 = 그날 평가 대상이 있고 실행률 ≥ 50%. Task 가 없는 날은 연속을 끊지도 늘리지도 않는다.
     * 현재 = 기간 끝(어제)부터 거꾸로.
     */
    static int[] streaks(PatternSampleSet set) {
        Map<LocalDate, RateCounter> byDate = Samples.byDate(set);
        int best = 0, run = 0;
        for (LocalDate d = set.range().start(); !d.isAfter(set.range().end()); d = d.plusDays(1)) {
            RateCounter c = byDate.get(d);
            if (c == null || c.total() == 0) continue;
            if (c.ratio() >= STREAK_DAY_RATE) {
                run++;
                best = Math.max(best, run);
            } else {
                run = 0;
            }
        }
        int current = 0;
        for (LocalDate d = set.range().end(); !d.isBefore(set.range().start()); d = d.minusDays(1)) {
            RateCounter c = byDate.get(d);
            if (c == null || c.total() == 0) continue;
            if (c.ratio() >= STREAK_DAY_RATE) current++;
            else break;
        }
        return new int[]{best, current};
    }

    /** 계획 시작 시각이 [fromHour, toHour) 인 완료 블록의 평균 시작 지연 */
    static Integer avgDelay(List<PatternTaskSample> tasks, int fromHour, int toHour) {
        long sum = 0;
        int n = 0;
        for (PatternTaskSample t : tasks) {
            Integer delay = t.startDelayMinutes();
            if (delay == null || delay < DELAY_MIN || delay > DELAY_MAX) continue;
            int h = t.startHour();
            if (h < fromHour || h >= toHour) continue;
            sum += delay;
            n++;
        }
        return n >= MIN_DELAY_SAMPLES ? Samples.round((double) sum / n) : null;
    }

    /**
     * [엔진 배치 블록 수, 그중 사용자가 그날이 끝나기 전에 바꾼 수].
     * 엔진 배치 블록 = 기간 안 날짜(to_date)에 놓인 ENGINE_PLACE 기록의 Task (같은 Task·날짜는 1개로).
     * 바꿈 = 그 엔진 배치 뒤, 그 날짜가 끝나기 전에 USER_MOVE / USER_UNSCHEDULE 기록이 있음.
     */
    static int[] engineEdits(PatternSampleSet set) {
        // key = taskId + 날짜, value = 마지막 엔진 배치 시각
        Map<String, LocalDateTime> engineAt = new LinkedHashMap<>();
        Map<String, LocalDate> engineDate = new HashMap<>();
        Set<String> edited = new HashSet<>();
        for (TaskScheduleLog l : set.scheduleLogs()) {
            if (l.getAction() == TaskScheduleAction.ENGINE_PLACE && set.range().contains(l.getToDate())) {
                String key = l.getTaskId() + "@" + l.getToDate();
                engineAt.put(key, l.getCreatedAt());
                engineDate.put(key, l.getToDate());
            }
        }
        for (TaskScheduleLog l : set.scheduleLogs()) {
            if (l.getAction() != TaskScheduleAction.USER_MOVE && l.getAction() != TaskScheduleAction.USER_UNSCHEDULE) continue;
            if (l.getFromDate() == null) continue;
            String key = l.getTaskId() + "@" + l.getFromDate();
            LocalDateTime placedAt = engineAt.get(key);
            if (placedAt == null) continue;
            LocalDateTime dayEnd = engineDate.get(key).plusDays(1).atStartOfDay();
            if (!l.getCreatedAt().isBefore(placedAt) && l.getCreatedAt().isBefore(dayEnd)) {
                edited.add(key);
            }
        }
        return new int[]{engineAt.size(), edited.size()};
    }
}
