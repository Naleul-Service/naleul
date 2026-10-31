package com.naleul.naleul.domain.pattern.calculator;

import com.naleul.naleul.domain.lifePattern.enums.LifePatternType;
import com.naleul.naleul.domain.pattern.enums.DayGroup;

import java.time.DayOfWeek;
import java.util.*;

/**
 * 잘 지키는 루틴 / 자주 놓치는 블록 (명세 4 · 2-7).
 *
 * 잘 지키는 루틴: 4번 이상 · 75% 이상 (예전 8번 · 80% 는 루틴을 만든 지 얼마 안 되면 거의 안 나왔어요)
 *
 * 자주 놓치는 블록 후보 (루틴마다 가장 낮은 것 하나)
 *  ① 루틴 × 특정 요일 : 그 요일 표본 3개 이상 · 실행률 ≤ 70%
 *  ② 루틴 × 평일/주말 × 시작 시각 : 표본 3개 이상 · 실행률 ≤ 70%
 *  ③ 둘 다 아니면 루틴 전체 : 3번 이상 · 2번 이상 놓침 · 실행률 ≤ 60% ("최근 5번 중 3번 놓쳤어요")
 * 요일 후보는 놓침의 40% 이상이 그 요일에 몰렸을 때만 쓴다 (아니면 요일 탓이 아님).
 * 잘 지키는 루틴에 든 루틴은 자주 놓치는 블록에서 빼요.
 */
public final class RoutineRankCalculator {

    static final int KEPT_MIN_SAMPLES = 4;
    static final int KEPT_MIN_RATE = 75;
    static final int MISSED_MAX_RATE = 70;
    static final int DAY_MIN_SAMPLES = 3;
    static final int HOUR_MIN_SAMPLES = 3;
    static final int OVERALL_MIN_SAMPLES = 3;
    static final int OVERALL_MIN_MISSES = 2;
    static final int OVERALL_MAX_RATE = 60;
    static final double DAY_MISS_SHARE = 0.4;
    static final int NEAR_PATTERN_MINUTES = 60;
    static final int MAX_ITEMS = 5;

    private RoutineRankCalculator() {
    }

    public static RoutineRankStats calculate(PatternSampleSet set) {
        Map<Long, List<PatternTaskSample>> byRoutine = new LinkedHashMap<>();
        for (PatternTaskSample t : set.tasks()) {
            if (t.isRoutine()) byRoutine.computeIfAbsent(t.routineId(), k -> new ArrayList<>()).add(t);
        }

        List<RoutineRankStats.Kept> kept = new ArrayList<>();
        List<RoutineRankStats.Missed> missed = new ArrayList<>();
        byRoutine.forEach((routineId, tasks) -> {
            RateCounter all = new RateCounter();
            tasks.forEach(t -> all.add(t.completed()));
            PatternTaskSample first = tasks.get(0);
            if (all.total() >= KEPT_MIN_SAMPLES && all.percent() >= KEPT_MIN_RATE) {
                kept.add(new RoutineRankStats.Kept(routineId, first.routineName(), first.displayEmoji(),
                        all.percent(), all.total()));
                return; // 잘 지키는 루틴은 놓치는 블록에서 빼요
            }
            RoutineRankStats.Missed m = worst(tasks, set.lifePatterns());
            if (m == null) m = overall(tasks, all);
            if (m != null) missed.add(m);
        });

        kept.sort(Comparator.comparingInt(RoutineRankStats.Kept::rate).reversed()
                .thenComparing(Comparator.comparingInt(RoutineRankStats.Kept::samples).reversed()));
        missed.sort(Comparator.comparingInt(RoutineRankStats.Missed::rate)
                .thenComparing(Comparator.comparingInt(RoutineRankStats.Missed::samples).reversed()));
        return new RoutineRankStats(top(kept), top(missed));
    }

    private static RoutineRankStats.Missed worst(List<PatternTaskSample> tasks, List<LifePatternSample> patterns) {
        PatternTaskSample first = tasks.get(0);
        int totalMisses = (int) tasks.stream().filter(t -> !t.completed()).count();
        if (totalMisses == 0) return null;
        RoutineRankStats.Missed best = null;

        // ① 요일
        Map<DayOfWeek, RateCounter> byDay = new EnumMap<>(DayOfWeek.class);
        tasks.forEach(t -> byDay.computeIfAbsent(t.dayOfWeek(), k -> new RateCounter()).add(t.completed()));
        for (Map.Entry<DayOfWeek, RateCounter> e : byDay.entrySet()) {
            RateCounter c = e.getValue();
            int dayMisses = c.total() - c.done();
            if (c.total() < DAY_MIN_SAMPLES || c.percent() > MISSED_MAX_RATE) continue;
            if (dayMisses < totalMisses * DAY_MISS_SHARE) continue;
            if (byDay.size() == 1) continue; // 한 요일에만 하는 루틴이면 요일 탓이라고 할 수 없음 → 시간 기준으로
            DayOfWeek d = e.getKey();
            RoutineRankStats.Missed m = new RoutineRankStats.Missed(first.routineId(), first.routineName(),
                    first.displayEmoji(), first.routineName() + " (" + TimeLabels.dayShort(d) + ")",
                    TimeLabels.dayLong(d) + "에 자주 놓쳐요", c.percent(), c.total(), d, null, null);
            if (best == null || m.rate() < best.rate()) best = m;
        }

        // ② 평일/주말 × 시작 시각
        Map<String, RateCounter> byHour = new LinkedHashMap<>();
        for (PatternTaskSample t : tasks) {
            if (!t.timed()) continue;
            DayGroup g = DayGroup.isWeekend(t.dayOfWeek()) ? DayGroup.WEEKEND : DayGroup.WEEKDAY;
            byHour.computeIfAbsent(g + ":" + t.startHour(), k -> new RateCounter()).add(t.completed());
        }
        for (Map.Entry<String, RateCounter> e : byHour.entrySet()) {
            RateCounter c = e.getValue();
            if (c.total() < HOUR_MIN_SAMPLES || c.percent() > MISSED_MAX_RATE) continue;
            String[] parts = e.getKey().split(":");
            DayGroup g = DayGroup.valueOf(parts[0]);
            int hour = Integer.parseInt(parts[1]);
            String hourText = String.format("%s %02d시", g.label(), hour);
            RoutineRankStats.Missed m = new RoutineRankStats.Missed(first.routineId(), first.routineName(),
                    first.displayEmoji(), first.routineName() + " (" + hourText + ")",
                    describeHour(hour, hourText, patterns), c.percent(), c.total(), null, g, hour);
            if (best == null || m.rate() < best.rate()) best = m;
        }
        return best;
    }

    /** ③ 요일 · 시간으로 묶을 수 없으면 루틴 전체로 — 자주 놓치는데 아무 데도 안 보이면 안 되니까요 */
    static RoutineRankStats.Missed overall(List<PatternTaskSample> tasks, RateCounter all) {
        int misses = all.total() - all.done();
        if (all.total() < OVERALL_MIN_SAMPLES || misses < OVERALL_MIN_MISSES || all.percent() > OVERALL_MAX_RATE) return null;
        PatternTaskSample first = tasks.get(0);
        return new RoutineRankStats.Missed(first.routineId(), first.routineName(), first.displayEmoji(),
                first.routineName(), "최근 " + all.total() + "번 중 " + misses + "번 놓쳤어요",
                all.percent(), all.total(), null, null, null);
    }

    /** 근처(±60분)에 수면·점심·저녁 외 생활 패턴이 있으면 "{패턴}과 가까운 시간" */
    static String describeHour(int hour, String hourText, List<LifePatternSample> patterns) {
        int minute = hour * 60 + 30;
        LifePatternSample near = null;
        int nearest = NEAR_PATTERN_MINUTES + 1;
        for (LifePatternSample p : patterns) {
            if (p.type() == LifePatternType.SLEEP) continue;
            int dist = p.distanceMinutes(minute);
            if (dist < nearest) {
                nearest = dist;
                near = p;
            }
        }
        if (near != null) return TimeLabels.withGwa(near.title()) + " 가까운 시간";
        return hourText + " 블록";
    }

    private static <T> List<T> top(List<T> list) {
        return list.size() > MAX_ITEMS ? List.copyOf(list.subList(0, MAX_ITEMS)) : list;
    }
}
