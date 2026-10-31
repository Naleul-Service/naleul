package com.naleul.naleul.domain.routine.util;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 루틴을 "주로 몇 시부터 몇 시에" 했는지 (2026-10, 목표 상세).
 *
 * 한 번 한 기록 = 완료한 루틴 Task 하나의 구간:
 *  ① 실제 시각(task_actual) → ② 계획 시각 → ③ 완료 누른 시각을 끝으로 보고 계획 길이(없으면 30분)만큼 앞으로
 * 하루 48칸(30분)에 기록이 겹친 분을 쌓고, "보통 한 번 하는 길이"만큼의 창 중 가장 많이 겹친 창을 고른다.
 * DB · 스프링 없는 순수 계산 → 단위 테스트 대상.
 */
public final class RoutineTimeStatsCalculator {

    static final int SLOT_MINUTES = 30;
    static final int SLOTS = 24 * 60 / SLOT_MINUTES;
    static final int DEFAULT_MINUTES = 30;
    static final int MAX_WINDOW_MINUTES = 240;

    private RoutineTimeStatsCalculator() {
    }

    /** 한 번 한 기록 (같은 날 안의 분 단위 구간, end > start) */
    public record Practice(long taskId, int startMinute, int endMinute, DayOfWeek day) {
        public Practice(long taskId, int startMinute, int endMinute) {
            this(taskId, startMinute, endMinute, null);
        }

        int minutes() {
            return endMinute - startMinute;
        }
    }

    /**
     * @param bestStart     가장 많이 한 시간대 시작 (HH:mm)
     * @param bestEnd       끝 (HH:mm, 24:00 이면 "24:00")
     * @param practiceCount 시각을 알 수 있는 실천 횟수
     * @param inWindowCount 그중 이 시간대에 걸친 횟수
     * @param hourly        0~23시 각 시간에 걸친 실천 횟수 (작은 막대그래프용)
     * @param byDay         요일별로 가장 많이 한 시간대 (많이 한 요일 순, 최대 3개) — "화 19:00~19:30 · 목 17:10~17:40"
     */
    public record TimeStats(String bestStart, String bestEnd, int practiceCount, int inWindowCount, List<Integer> hourly,
                            List<DayStat> byDay) {
        public TimeStats(String bestStart, String bestEnd, int practiceCount, int inWindowCount, List<Integer> hourly) {
            this(bestStart, bestEnd, practiceCount, inWindowCount, hourly, List.of());
        }
    }

    /**
     * 한 요일에서 가장 많이 한 시간대
     * @param day   MON ~ SUN
     * @param count 그 요일에 한 횟수 중 이 시간대에 걸친 횟수
     * @param total 그 요일에 한 횟수
     */
    public record DayStat(String day, String start, String end, int count, int total) {
    }

    static final int MAX_DAYS = 3;

    /**
     * DB 행 → 실천 목록.
     * row = [routineId, taskId, plannedStartAt, plannedEndAt, completedAt, actualStartAt, actualEndAt]
     */
    public static Map<Long, List<Practice>> practicesByRoutine(List<Object[]> rows) {
        // taskId 별로 모은다 (task_actual 이 여러 줄이면 모두 실제 구간)
        Map<Long, Long> routineOf = new LinkedHashMap<>();
        Map<Long, List<LocalDateTime[]>> actuals = new LinkedHashMap<>();
        Map<Long, LocalDateTime[]> fallback = new LinkedHashMap<>();
        for (Object[] r : rows) {
            Long routineId = (Long) r[0];
            Long taskId = (Long) r[1];
            if (routineId == null || taskId == null) continue;
            routineOf.put(taskId, routineId);
            LocalDateTime ps = (LocalDateTime) r[2], pe = (LocalDateTime) r[3], done = (LocalDateTime) r[4];
            LocalDateTime as = (LocalDateTime) r[5], ae = (LocalDateTime) r[6];
            if (as != null && ae != null && ae.isAfter(as)) {
                actuals.computeIfAbsent(taskId, k -> new ArrayList<>()).add(new LocalDateTime[]{as, ae});
            }
            if (!fallback.containsKey(taskId)) {
                if (ps != null && pe != null && pe.isAfter(ps)) {
                    fallback.put(taskId, new LocalDateTime[]{ps, pe});
                } else if (done != null) {
                    long len = ps != null && pe != null ? Math.max(Duration.between(ps, pe).toMinutes(), 1) : DEFAULT_MINUTES;
                    fallback.put(taskId, new LocalDateTime[]{done.minusMinutes(len), done});
                }
            }
        }
        Map<Long, List<Practice>> out = new LinkedHashMap<>();
        for (Map.Entry<Long, Long> e : routineOf.entrySet()) {
            long taskId = e.getKey();
            List<LocalDateTime[]> ranges = actuals.containsKey(taskId) ? actuals.get(taskId)
                    : fallback.containsKey(taskId) ? List.<LocalDateTime[]>of(fallback.get(taskId)) : List.of();
            for (LocalDateTime[] range : ranges) {
                Practice p = toPractice(taskId, range[0], range[1]);
                if (p != null) out.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(p);
            }
        }
        return out;
    }

    /** 자정을 넘기면 시작한 날 24:00 까지만 본다 */
    static Practice toPractice(long taskId, LocalDateTime start, LocalDateTime end) {
        int s = start.getHour() * 60 + start.getMinute();
        long len = Duration.between(start, end).toMinutes();
        if (len <= 0) return null;
        int e = (int) Math.min(24L * 60, s + len);
        return e > s ? new Practice(taskId, s, e, start.getDayOfWeek()) : null;
    }

    public static TimeStats calculate(List<Practice> practices) {
        TimeStats all = calculateOverall(practices);
        if (all == null) return null;
        return new TimeStats(all.bestStart(), all.bestEnd(), all.practiceCount(), all.inWindowCount(), all.hourly(),
                byDay(practices));
    }

    /** 요일별 최빈 시간대 — 그 요일에 2번 이상 한 요일을 먼저, 많이 한 순으로 최대 3개 (없으면 1번 한 요일) */
    static List<DayStat> byDay(List<Practice> practices) {
        Map<DayOfWeek, List<Practice>> byDow = new java.util.EnumMap<>(DayOfWeek.class);
        for (Practice p : practices) {
            if (p.day() != null) byDow.computeIfAbsent(p.day(), k -> new ArrayList<>()).add(p);
        }
        List<DayStat> out = new ArrayList<>();
        byDow.forEach((dow, list) -> {
            TimeStats t = calculateOverall(list);
            if (t != null) out.add(new DayStat(dow.name(), t.bestStart(), t.bestEnd(), t.inWindowCount(), t.practiceCount()));
        });
        boolean anyRepeated = out.stream().anyMatch(d -> d.total() >= 2);
        return out.stream()
                .filter(d -> !anyRepeated || d.total() >= 2)
                .sorted(java.util.Comparator.comparingInt(DayStat::count).reversed()
                        .thenComparing(java.util.Comparator.comparingInt(DayStat::total).reversed())
                        .thenComparing(d -> DayOfWeek.valueOf(d.day())))
                .limit(MAX_DAYS)
                .toList();
    }

    static TimeStats calculateOverall(List<Practice> practices) {
        if (practices == null || practices.isEmpty()) return null;

        // 하루 48칸에 겹친 분
        long[] slot = new long[SLOTS];
        for (Practice p : practices) {
            for (int i = p.startMinute() / SLOT_MINUTES; i < SLOTS && i * SLOT_MINUTES < p.endMinute(); i++) {
                int from = Math.max(p.startMinute(), i * SLOT_MINUTES);
                int to = Math.min(p.endMinute(), (i + 1) * SLOT_MINUTES);
                if (to > from) slot[i] += to - from;
            }
        }

        // 보통 한 번 하는 길이 (중앙값, 30분 단위 올림, 30분~4시간)
        List<Integer> lengths = new ArrayList<>(practices.stream().map(Practice::minutes).toList());
        lengths.sort(Integer::compare);
        int median = lengths.get(lengths.size() / 2);
        int window = Math.max(SLOT_MINUTES, Math.min(MAX_WINDOW_MINUTES,
                (int) Math.ceil(median / (double) SLOT_MINUTES) * SLOT_MINUTES));
        int width = window / SLOT_MINUTES;

        int bestStart = 0;
        long bestSum = -1;
        for (int s = 0; s + width <= SLOTS; s++) {
            long sum = 0;
            for (int i = s; i < s + width; i++) sum += slot[i];
            if (sum > bestSum) {
                bestSum = sum;
                bestStart = s;
            }
        }
        int winStart = bestStart * SLOT_MINUTES, winEnd = winStart + window;

        // 같은 Task 를 나눠서 한 기록은 한 번으로 센다
        java.util.Set<Long> tasks = new java.util.HashSet<>(), inWindow = new java.util.HashSet<>();
        int[] hourly = new int[24];
        Map<Long, boolean[]> hoursByTask = new LinkedHashMap<>();
        for (Practice p : practices) {
            tasks.add(p.taskId());
            if (p.startMinute() < winEnd && p.endMinute() > winStart) inWindow.add(p.taskId());
            boolean[] h = hoursByTask.computeIfAbsent(p.taskId(), k -> new boolean[24]);
            for (int hr = p.startMinute() / 60; hr < 24 && hr * 60 < p.endMinute(); hr++) h[hr] = true;
        }
        for (boolean[] h : hoursByTask.values()) for (int i = 0; i < 24; i++) if (h[i]) hourly[i]++;

        List<Integer> hourlyList = new ArrayList<>(24);
        for (int v : hourly) hourlyList.add(v);
        return new TimeStats(hm(winStart), hm(winEnd), tasks.size(), inWindow.size(), hourlyList);
    }

    private static String hm(int minute) {
        if (minute >= 24 * 60) return "24:00";
        return LocalTime.of(minute / 60, minute % 60).toString();
    }
}
