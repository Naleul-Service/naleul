package com.naleul.naleul.domain.goalProgress.service;

import com.naleul.naleul.domain.pattern.calculator.TimeLabels;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 목표 주간 점검 — "계획이 사람에게 맞춰 가기" (DB 없이 순수 계산).
 *
 * 지난 2주(이번 주 제외, 월~일) 이 목표 루틴 실천률을 본다.
 *  - 2주 연속 60% 미만 → 가장 안 되는 루틴의 가장 안 되는 요일 하나를 빼자 (루틴이 2일 이상일 때만)
 *  - 2주 연속 90% 이상 → 가장 잘 되는 루틴에 요일 하나를 더하자 (5일 이하일 때만, 쉬는 날 간격이 가장 넓은 요일)
 * 주마다 해야 했던 루틴이 3개 미만이면 판단하지 않는다. 이미 반영했거나 7일 안에 미룬 제안은 다시 내지 않는다.
 */
public final class GoalAdjustmentFinder {

    static final int DOWN_BELOW = 60;
    static final int UP_FROM = 90;
    static final int MIN_DUE_PER_WEEK = 3;
    static final int UP_MAX_DAYS = 5;

    private GoalAdjustmentFinder() {
    }

    public record RoutineState(Long routineId, String name, Set<DayOfWeek> days, boolean active) {
    }

    /** 해야 했던 루틴 하루치 */
    public record Due(Long routineId, LocalDate date, boolean done) {
    }

    public enum Direction { DOWN, UP }

    public record Suggestion(String key, Direction direction, Long routineId, String routineName,
                             int fromDays, int toDays, DayOfWeek day,
                             int previousWeekRate, int lastWeekRate) {

        /** 화면 문장 — "최근 2주 루틴 실천률이 45% · 50%예요. ‘헬스’를 주 4회 → 3회로 줄여 볼까요? (월요일 빼기)" */
        public String message() {
            // "‘헬스’를" — 조사는 루틴 이름의 받침으로 고른다
            String name = "‘" + routineName + "’" + TimeLabels.withEulReul(routineName).substring(routineName.length());
            if (direction == Direction.DOWN) {
                return "최근 2주 루틴 실천률이 " + previousWeekRate + "% · " + lastWeekRate + "%예요. "
                        + name + " 주 " + fromDays + "회 → " + toDays + "회로 줄여 볼까요? ("
                        + TimeLabels.dayLong(day) + " 빼기)";
            }
            return "최근 2주 루틴을 " + previousWeekRate + "% · " + lastWeekRate + "% 지켰어요. "
                    + name + " 주 " + fromDays + "회 → " + toDays + "회로 늘려 볼까요? ("
                    + TimeLabels.dayLong(day) + " 추가)";
        }
    }

    public static Optional<Suggestion> find(LocalDate today, List<RoutineState> routines, List<Due> dues,
                                            Set<String> blockedKeys) {
        Map<Long, RoutineState> byId = routines.stream()
                .filter(RoutineState::active)
                .collect(Collectors.toMap(RoutineState::routineId, r -> r, (a, b) -> a, LinkedHashMap::new));
        if (byId.isEmpty()) return Optional.empty();

        LocalDate lastWeek = weekStart(today).minusWeeks(1);
        LocalDate prevWeek = lastWeek.minusWeeks(1);
        List<Due> mine = dues.stream().filter(d -> byId.containsKey(d.routineId())).toList();
        List<Due> w1 = between(mine, prevWeek, prevWeek.plusDays(6));
        List<Due> w2 = between(mine, lastWeek, lastWeek.plusDays(6));
        if (w1.size() < MIN_DUE_PER_WEEK || w2.size() < MIN_DUE_PER_WEEK) return Optional.empty();
        int r1 = rate(w1), r2 = rate(w2);

        List<Due> twoWeeks = new ArrayList<>(w1);
        twoWeeks.addAll(w2);
        Map<Long, List<Due>> perRoutine = twoWeeks.stream().collect(Collectors.groupingBy(Due::routineId));

        if (r1 < DOWN_BELOW && r2 < DOWN_BELOW) {
            List<RoutineState> candidates = perRoutine.entrySet().stream()
                    .filter(e -> e.getValue().size() >= 2)
                    .map(e -> byId.get(e.getKey()))
                    .filter(r -> r.days().size() >= 2)
                    .sorted(Comparator.comparingInt((RoutineState r) -> rate(perRoutine.get(r.routineId())))
                            .thenComparing(r -> -r.days().size()))
                    .toList();
            List<Due> fourWeeks = between(mine, lastWeek.minusWeeks(3), lastWeek.plusDays(6));
            for (RoutineState r : candidates) {
                DayOfWeek day = weakestDay(r, fourWeeks);
                if (day == null) continue;
                Suggestion s = new Suggestion(key(r.routineId(), Direction.DOWN, day, lastWeek), Direction.DOWN,
                        r.routineId(), r.name(), r.days().size(), r.days().size() - 1, day, r1, r2);
                if (!blockedKeys.contains(s.key())) return Optional.of(s);
            }
        }

        if (r1 >= UP_FROM && r2 >= UP_FROM) {
            List<RoutineState> candidates = perRoutine.entrySet().stream()
                    .filter(e -> e.getValue().size() >= 2)
                    .map(e -> byId.get(e.getKey()))
                    .filter(r -> !r.days().isEmpty() && r.days().size() <= UP_MAX_DAYS)
                    .sorted(Comparator.comparingInt((RoutineState r) -> -rate(perRoutine.get(r.routineId())))
                            .thenComparing(r -> r.days().size()))
                    .toList();
            for (RoutineState r : candidates) {
                DayOfWeek day = roomiestDay(r.days());
                if (day == null) continue;
                Suggestion s = new Suggestion(key(r.routineId(), Direction.UP, day, lastWeek), Direction.UP,
                        r.routineId(), r.name(), r.days().size(), r.days().size() + 1, day, r1, r2);
                if (!blockedKeys.contains(s.key())) return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    /** "ADJ:12:DOWN:MONDAY:2026-09-28" */
    public static String key(Long routineId, Direction d, DayOfWeek day, LocalDate lastWeek) {
        return "ADJ:" + routineId + ":" + d + ":" + day + ":" + lastWeek;
    }

    /** 최근 4주 그 루틴 요일 중 실천률이 가장 낮은 요일 (같으면 놓친 횟수가 많은 쪽, 그다음 주 뒤쪽 요일) */
    static DayOfWeek weakestDay(RoutineState r, List<Due> fourWeeks) {
        Map<DayOfWeek, List<Due>> byDay = fourWeeks.stream()
                .filter(d -> d.routineId().equals(r.routineId()))
                .filter(d -> r.days().contains(d.date().getDayOfWeek()))
                .collect(Collectors.groupingBy(d -> d.date().getDayOfWeek()));
        return byDay.entrySet().stream()
                .min(Comparator.comparingInt((Map.Entry<DayOfWeek, List<Due>> e) -> rate(e.getValue()))
                        .thenComparing(e -> -misses(e.getValue()))
                        .thenComparing(e -> -e.getKey().getValue()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * 이미 있는 요일들과 가장 멀리 떨어진 빈 요일 (쉬는 날 간격 유지).
     * 가장 가까운 요일까지의 거리가 같으면 모든 요일과의 거리 합이 큰 쪽, 그것도 같으면 앞 요일.
     * 예) 월·수·금 → 빈 요일은 모두 하루 거리라, 거리 합이 큰 토요일
     */
    static DayOfWeek roomiestDay(Set<DayOfWeek> days) {
        DayOfWeek best = null;
        int bestGap = -1, bestSum = -1;
        for (DayOfWeek d : DayOfWeek.values()) {
            if (days.contains(d)) continue;
            int gap = days.stream().mapToInt(x -> circular(d, x)).min().orElse(7);
            int sum = days.stream().mapToInt(x -> circular(d, x)).sum();
            if (gap > bestGap || (gap == bestGap && sum > bestSum)) {
                bestGap = gap;
                bestSum = sum;
                best = d;
            }
        }
        return best;
    }

    private static int circular(DayOfWeek a, DayOfWeek b) {
        int diff = Math.abs(a.getValue() - b.getValue());
        return Math.min(diff, 7 - diff);
    }

    private static LocalDate weekStart(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static List<Due> between(List<Due> dues, LocalDate from, LocalDate to) {
        return dues.stream().filter(d -> !d.date().isBefore(from) && !d.date().isAfter(to)).toList();
    }

    private static int rate(List<Due> dues) {
        if (dues.isEmpty()) return 0;
        return (int) Math.round(100.0 * dues.stream().filter(Due::done).count() / dues.size());
    }

    private static int misses(List<Due> dues) {
        return (int) dues.stream().filter(d -> !d.done()).count();
    }
}
