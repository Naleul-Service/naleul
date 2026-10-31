package com.naleul.naleul.domain.routine.util;

import com.naleul.naleul.domain.task.enums.TaskStatus;

import java.time.LocalDate;
import java.util.*;

/**
 * 루틴 실천률 · 연속 기록 (2026-10, 루틴 실천 알림).
 * 루틴 Task 는 반복 요일에만 생기므로 "인스턴스가 이어서 COMPLETED 인 개수"가 곧 연속 실천일이다.
 * 오늘 인스턴스는 아직 하루가 안 끝나서 넣지 않는다 (호출하는 쪽이 before = 오늘 로 조회).
 */
public final class RoutinePracticeStats {

    private RoutinePracticeStats() {}

    /** 루틴 하나의 지난 기록 */
    public record Stats(int total, int done, int streak, Map<LocalDate, Instance> byDate) {
        /** 실천률 (0~100, 기록이 없으면 0) */
        public int rate() {
            return total == 0 ? 0 : Math.round(done * 100f / total);
        }

        /** 오늘도 하면 실천률이 얼마가 되는지 */
        public int rateIfDoneToday() {
            return Math.round((done + 1) * 100f / (total + 1));
        }

        public Optional<Instance> on(LocalDate date) {
            return Optional.ofNullable(byDate.get(date));
        }
    }

    public record Instance(long taskId, LocalDate date, TaskStatus status) {
        public boolean done() {
            return status == TaskStatus.COMPLETED;
        }
    }

    /** rows: [routineId, taskId, routineDate, taskStatus] */
    public static Map<Long, Stats> of(List<Object[]> rows) {
        Map<Long, List<Instance>> byRoutine = new HashMap<>();
        for (Object[] r : rows) {
            if (r[0] == null || r[2] == null) continue;
            long routineId = ((Number) r[0]).longValue();
            long taskId = ((Number) r[1]).longValue();
            LocalDate date = (LocalDate) r[2];
            TaskStatus status = r[3] instanceof TaskStatus ts ? ts : TaskStatus.valueOf(String.valueOf(r[3]));
            byRoutine.computeIfAbsent(routineId, k -> new ArrayList<>()).add(new Instance(taskId, date, status));
        }
        Map<Long, Stats> out = new HashMap<>();
        byRoutine.forEach((routineId, list) -> out.put(routineId, statsOf(list)));
        return out;
    }

    static Stats statsOf(List<Instance> list) {
        // 같은 날 인스턴스가 여럿이면(쪼갠 루틴) 하나라도 했으면 한 날로
        Map<LocalDate, Instance> byDate = new TreeMap<>();
        for (Instance i : list) {
            byDate.merge(i.date(), i, (a, b) -> a.done() ? a : b);
        }
        int total = byDate.size();
        int done = (int) byDate.values().stream().filter(Instance::done).count();
        List<LocalDate> dates = new ArrayList<>(byDate.keySet());
        int streak = 0;
        for (int i = dates.size() - 1; i >= 0; i--) {
            if (!byDate.get(dates.get(i)).done()) break;
            streak++;
        }
        return new Stats(total, done, streak, Collections.unmodifiableMap(byDate));
    }

    /** 실천률이 가장 높은 루틴인지 (기록 3번 이상인 루틴 중에서, 동률이면 같이 1등) */
    public static boolean isTop(long routineId, Map<Long, Stats> all) {
        Stats mine = all.get(routineId);
        if (mine == null || mine.total() < 3) return false;
        int best = all.values().stream().filter(s -> s.total() >= 3).mapToInt(Stats::rate).max().orElse(0);
        return mine.rate() >= best && all.values().stream().filter(s -> s.total() >= 3).count() > 1;
    }
}
