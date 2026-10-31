package com.naleul.naleul.domain.pattern.calculator;

import com.naleul.naleul.domain.pattern.entity.TaskScheduleLog;
import com.naleul.naleul.domain.pattern.enums.DayGroup;
import com.naleul.naleul.domain.pattern.enums.PatternSuggestionType;
import com.naleul.naleul.domain.pattern.enums.StyleTimeStrength;
import com.naleul.naleul.domain.pattern.enums.TaskScheduleAction;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 하단 제안 찾기 (명세 4 · 4-1, 4-2). 순수 계산 — 겹침 검사는 ConflictChecker 로 받는다.
 *
 * ROUTINE_TIME 루틴 시간 변경
 *   대상: 시간이 있는 진행 중 루틴, TimeBlock 표본 5개 이상, 실행률 70% 이하
 *   목적지: ① 사용자가 같은 날 안에서 3번 이상 옮긴 시각(최빈값)
 *          ② 루틴 시각이 취약 시간이면 골든 타임 안에서 겹치지 않는 첫 정각
 * ROUTINE_DAY 루틴 요일 변경
 *   대상: 루틴의 특정 요일(표본 3개 이상) 실행률이 루틴 평균보다 15%p 이상 낮음
 *   목적지: ① 그 요일에서 다른 요일로 2번 이상 옮긴 요일(최빈값)
 *          ② 루틴이 없는 같은 묶음(평일/주말) 요일 중 사용자 실행률이 가장 높은 요일
 * 공통: 앞으로 2주 겹침 비율 50% 이상이면 버림, 루틴마다 기대 효과가 가장 큰 것 1개, 전체 상위 2개
 */
public final class SuggestionFinder {

    static final int TIME_MIN_SAMPLES = 5;
    static final int TIME_MAX_RATE = 70;
    static final int TIME_MIN_MOVES = 3;
    static final int DAY_MIN_SAMPLES = 3;
    static final int DAY_GAP = 15;
    static final int DAY_MIN_MOVES = 2;
    static final int TARGET_MIN_SAMPLES = 3;
    static final int MIN_IMPROVEMENT = 5;
    static final double MAX_CONFLICT = 0.5;
    static final int MAX_ITEMS = 2;
    static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private SuggestionFinder() {
    }

    public static SuggestionResult find(PatternSampleSet set, PatternStats stats, StyleResult style,
                                        List<RoutineInfo> routines, Set<String> blockedKeys,
                                        LocalDate tomorrow, ConflictChecker checker) {
        int evaluated = set.tasks().size();
        if (evaluated == 0 || routines.isEmpty()) return null;

        Map<Long, List<PatternTaskSample>> byRoutine = new HashMap<>();
        set.tasks().forEach(t -> { if (t.isRoutine()) byRoutine.computeIfAbsent(t.routineId(), k -> new ArrayList<>()).add(t); });
        Map<Long, List<TaskScheduleLog>> moves = new HashMap<>();
        for (TaskScheduleLog l : set.scheduleLogs()) {
            if (l.getAction() == TaskScheduleAction.USER_MOVE && l.getRoutineId() != null
                    && l.getFromStartAt() != null && l.getToStartAt() != null && set.range().contains(l.getFromDate())) {
                moves.computeIfAbsent(l.getRoutineId(), k -> new ArrayList<>()).add(l);
            }
        }

        List<Suggestion> best = new ArrayList<>();
        for (RoutineInfo r : routines) {
            if (!r.active() || r.repeatEndDate() == null || r.repeatEndDate().isBefore(tomorrow)) continue;
            List<PatternTaskSample> samples = byRoutine.getOrDefault(r.routineId(), List.of());
            List<TaskScheduleLog> rMoves = moves.getOrDefault(r.routineId(), List.of());
            Suggestion a = timeSuggestion(set, stats, r, samples, rMoves, evaluated, checker);
            Suggestion b = daySuggestion(stats, r, samples, rMoves, evaluated, checker);
            Suggestion pick = null;
            for (Suggestion s : new Suggestion[]{a, b}) {
                if (s == null || blockedKeys.contains(s.key())) continue;
                if (pick == null || s.gain() > pick.gain()) pick = s;
            }
            if (pick != null) best.add(pick);
        }
        if (best.isEmpty()) return null;
        best.sort(Comparator.comparingDouble(Suggestion::gain).reversed());
        List<Suggestion> items = best.size() > MAX_ITEMS ? List.copyOf(best.subList(0, MAX_ITEMS)) : best;
        double gain = items.stream().mapToDouble(Suggestion::gain).sum();
        Integer expected = gain >= 1 ? (int) Math.round(gain) : null;
        return new SuggestionResult(text(stats, style, items, expected), expected, items);
    }

    // ── ROUTINE_TIME ──────────────────────────────────────

    static Suggestion timeSuggestion(PatternSampleSet set, PatternStats stats, RoutineInfo r,
                                     List<PatternTaskSample> samples, List<TaskScheduleLog> rMoves,
                                     int evaluated, ConflictChecker checker) {
        if (r.start() == null) return null;
        RateCounter cur = new RateCounter();
        samples.stream().filter(PatternTaskSample::timed).forEach(t -> cur.add(t.completed()));
        if (cur.total() < TIME_MIN_SAMPLES || cur.percent() > TIME_MAX_RATE) return null;

        Set<DayOfWeek> days = r.effectiveDays();
        DayGroup group = groupOf(days);
        LocalTime target = null;
        Integer targetRate = null;

        // ① 사용자가 직접 자주 옮긴 시각
        List<TaskScheduleLog> sameDay = rMoves.stream().filter(l -> !l.isDayChanged()).toList();
        if (sameDay.size() >= TIME_MIN_MOVES) {
            String mode = BlockKeepingCalculator.mode(sameDay, l -> TimeLabels.hhmm10(l.getToStartAt()));
            long count = sameDay.stream().filter(l -> TimeLabels.hhmm10(l.getToStartAt()).equals(mode)).count();
            if (count >= TIME_MIN_MOVES) target = LocalTime.parse(mode);
        }
        // ② 취약 시간 → 골든 타임
        HeatmapStats h = stats.heatmap();
        if (target == null && h != null && h.weakTime() != null && h.goldenTime() != null) {
            HeatmapStats.TimeWindow weak = h.weakTime(), golden = h.goldenTime();
            int hour = r.start().getHour();
            boolean inWeak = hour >= weak.startHour() && hour < weak.endHour();
            boolean groupFits = golden.dayGroup() == DayGroup.ALL || golden.dayGroup() == group;
            if (inWeak && groupFits) {
                for (int gh = golden.startHour(); gh < golden.endHour(); gh++) {
                    LocalTime t = LocalTime.of(gh, 0);
                    if (checker.conflictRatio(r, days, t, t.plusMinutes(r.duration())) < MAX_CONFLICT) {
                        target = t;
                        targetRate = golden.rate();
                        break;
                    }
                }
            }
        }
        if (target == null || target.equals(r.start())) return null;

        LocalTime newEnd = target.plusMinutes(r.duration());
        if (checker.conflictRatio(r, days, target, newEnd) >= MAX_CONFLICT) return null;
        Integer hourRate = hourRate(set, group, target.getHour());
        if (hourRate != null) targetRate = hourRate;
        if (targetRate == null && stats.summary() != null) targetRate = stats.summary().executionRate();
        if (targetRate == null || targetRate - cur.percent() < MIN_IMPROVEMENT) return null;

        double gain = (double) cur.total() * (targetRate - cur.percent()) / evaluated;
        String at = target.format(HHMM);
        String groupText = group == DayGroup.ALL ? "" : group.label() + " ";
        String phrase = "**" + TimeLabels.withEulReul(r.name()) + " " + TimeLabels.withRo(groupText + at) + "**";
        return new Suggestion("ROUTINE_TIME:" + r.routineId() + ":" + at, PatternSuggestionType.ROUTINE_TIME,
                r.routineId(), r.name(), r.start(), r.end(), target, newEnd, null, null, gain, phrase);
    }

    /** 그 요일 묶음·시각에 시작한 TimeBlock 실행률 (표본 3개 미만이면 null) */
    static Integer hourRate(PatternSampleSet set, DayGroup group, int hour) {
        RateCounter c = new RateCounter();
        for (PatternTaskSample t : set.tasks()) {
            if (t.timed() && t.startHour() == hour && group.contains(t.dayOfWeek())) c.add(t.completed());
        }
        return c.total() >= TARGET_MIN_SAMPLES ? c.percent() : null;
    }

    // ── ROUTINE_DAY ───────────────────────────────────────

    static Suggestion daySuggestion(PatternStats stats, RoutineInfo r, List<PatternTaskSample> samples,
                                    List<TaskScheduleLog> rMoves, int evaluated, ConflictChecker checker) {
        Set<DayOfWeek> days = r.effectiveDays();
        if (days.size() >= 7 || samples.isEmpty()) return null;
        RateCounter all = new RateCounter();
        Map<DayOfWeek, RateCounter> byDay = new EnumMap<>(DayOfWeek.class);
        for (PatternTaskSample t : samples) {
            all.add(t.completed());
            byDay.computeIfAbsent(t.dayOfWeek(), k -> new RateCounter()).add(t.completed());
        }

        Suggestion best = null;
        for (Map.Entry<DayOfWeek, RateCounter> e : byDay.entrySet()) {
            DayOfWeek from = e.getKey();
            RateCounter c = e.getValue();
            if (!days.contains(from) || c.total() < DAY_MIN_SAMPLES) continue;
            if (all.percent() - c.percent() < DAY_GAP) continue;

            DayOfWeek to = movedDay(rMoves, from, days);
            if (to == null) to = bestFreeDay(stats.weekday(), days, c.percent(), groupOf(days));
            if (to == null) continue;

            // 기대 실행률 = 이 루틴의 나머지 요일 실행률
            RateCounter others = new RateCounter();
            byDay.forEach((d, rc) -> { if (d != from) others.merge(rc); });
            if (others.total() == 0) continue;
            int expected = others.percent();
            if (expected - c.percent() < MIN_IMPROVEMENT) continue;

            Set<DayOfWeek> newDays = EnumSet.copyOf(days);
            newDays.remove(from);
            newDays.add(to);
            if (r.start() != null
                    && checker.conflictRatio(r, EnumSet.of(to), r.start(), r.start().plusMinutes(r.duration())) >= MAX_CONFLICT) {
                continue;
            }
            double gain = (double) c.total() * (expected - c.percent()) / evaluated;
            String phrase = "**" + TimeLabels.dayLong(from) + " " + TimeLabels.withEunNeun(r.name()) + " "
                    + TimeLabels.withRo(TimeLabels.dayLong(to)) + "**";
            Suggestion s = new Suggestion("ROUTINE_DAY:" + r.routineId() + ":" + from + ":" + to,
                    PatternSuggestionType.ROUTINE_DAY, r.routineId(), r.name(), null, null, null, null,
                    sorted(days), sorted(newDays), gain, phrase);
            if (best == null || s.gain() > best.gain()) best = s;
        }
        return best;
    }

    /** 그 요일에서 다른 요일(루틴이 없는 요일)로 2번 이상 옮겼으면 그 요일 */
    private static DayOfWeek movedDay(List<TaskScheduleLog> moves, DayOfWeek from, Set<DayOfWeek> days) {
        List<TaskScheduleLog> fromDay = moves.stream()
                .filter(l -> l.isDayChanged() && l.getFromDate().getDayOfWeek() == from
                        && !days.contains(l.getToDate().getDayOfWeek()))
                .toList();
        if (fromDay.size() < DAY_MIN_MOVES) return null;
        DayOfWeek mode = BlockKeepingCalculator.mode(fromDay, l -> l.getToDate().getDayOfWeek());
        long count = fromDay.stream().filter(l -> l.getToDate().getDayOfWeek() == mode).count();
        return count >= DAY_MIN_MOVES ? mode : null;
    }

    /**
     * 루틴이 없는 요일 중 사용자 실행률이 가장 높은 요일 (지금 요일보다 높아야 함).
     * 평일 루틴은 평일 안에서, 주말 루틴은 주말 안에서만 찾는다 (평일 공부를 토요일로 보내는 제안 방지).
     */
    private static DayOfWeek bestFreeDay(WeekdayStats w, Set<DayOfWeek> days, int currentRate, DayGroup group) {
        if (w == null) return null;
        WeekdayStats.Day best = null;
        for (WeekdayStats.Day d : w.days()) {
            if (days.contains(d.dayOfWeek()) || !group.contains(d.dayOfWeek()) || d.rate() == null || d.samples() < 5) continue;
            if (best == null || d.rate() > best.rate()) best = d;
        }
        return best != null && best.rate() > currentRate ? best.dayOfWeek() : null;
    }

    // ── 문장 ──────────────────────────────────────────────

    /**
     * "아침 블록을 자주 놓치고 저녁에 강한 패턴이에요. **토익 RC를 평일 12:40으로** 고정하고
     *  **목요일 근력 운동은 금요일로** 옮기면 실행률이 **약 6%p** 오를 것으로 예상돼요."
     */
    static String text(PatternStats stats, StyleResult style, List<Suggestion> items, Integer expected) {
        StringBuilder sb = new StringBuilder();
        HeatmapStats h = stats.heatmap();
        boolean weak = h != null && h.weakTime() != null;
        boolean strong = style != null && style.ready() && style.timeStrength() != StyleTimeStrength.EVEN;
        if (weak || strong) {
            if (weak) sb.append(TimeLabels.slotName(h.weakTime().startHour())).append(" 블록을 자주 놓치고 ");
            sb.append(strong ? style.timeStrength().modifier() + " 패턴이에요. " : "있어요. ");
        }
        for (int i = 0; i < items.size(); i++) {
            Suggestion s = items.get(i);
            boolean last = i == items.size() - 1;
            String verb = s.type() == PatternSuggestionType.ROUTINE_TIME
                    ? (last ? "고정하면" : "고정하고")
                    : (last ? "옮기면" : "옮기고");
            sb.append(s.phrase()).append(' ').append(verb).append(' ');
        }
        if (expected != null) {
            sb.append("실행률이 **약 ").append(expected).append("%p** 오를 것으로 예상돼요.");
        } else {
            sb.append("실행률을 높이는 데 도움이 될 거예요.");
        }
        return sb.toString();
    }

    static DayGroup groupOf(Set<DayOfWeek> days) {
        boolean allWeekday = days.stream().noneMatch(DayGroup::isWeekend);
        boolean allWeekend = days.stream().allMatch(DayGroup::isWeekend);
        if (allWeekday) return DayGroup.WEEKDAY;
        if (allWeekend) return DayGroup.WEEKEND;
        return DayGroup.ALL;
    }

    private static List<DayOfWeek> sorted(Set<DayOfWeek> days) {
        return days.stream().sorted().toList();
    }
}
