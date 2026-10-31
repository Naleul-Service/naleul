package com.naleul.naleul.domain.pattern.calculator;

import com.naleul.naleul.domain.pattern.entity.TaskScheduleLog;
import com.naleul.naleul.domain.pattern.enums.DayGroup;
import com.naleul.naleul.domain.pattern.enums.TaskScheduleAction;

import java.time.DayOfWeek;
import java.util.*;
import java.util.function.Function;

/** 제시간 / 늦게 / 놓침 비율 + 자주 옮기는 블록 (명세 4 · 2-6) */
public final class BlockKeepingCalculator {

    /** 이 분까지 늦으면 "제시간" */
    public static final int ON_TIME_MAX_DELAY = 10;
    static final int MIN_MOVE_COUNT = 2;
    static final int MAX_MOVES = 3;
    static final int MAX_ITEMS = 40;

    private BlockKeepingCalculator() {
    }

    public static BlockKeepingStats calculate(PatternSampleSet set) {
        int onTime = 0, late = 0, missed = 0;
        // 루틴 · Task 별 횟수 [제시간, 늦게, 놓침]
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, PatternTaskSample> info = new HashMap<>();
        Map<Long, String> keyByTask = new HashMap<>();
        for (PatternTaskSample t : set.tasks()) {
            if (!t.timed()) continue;
            int kind;
            if (!t.completed()) {
                missed++;
                kind = 2;
            } else if (t.startDelayMinutes() > ON_TIME_MAX_DELAY) {
                late++;
                kind = 1;
            } else {
                onTime++; // 실제 시각 기록이 없으면 지연 0분 → 제시간
                kind = 0;
            }
            String key = itemKey(t);
            counts.computeIfAbsent(key, k -> new int[3])[kind]++;
            info.putIfAbsent(key, t);
            if (t.taskId() != null) keyByTask.put(t.taskId(), key);
        }
        for (CarryMissSample m : set.carryMisses()) {
            if (!m.timed() || !set.range().contains(m.date())) continue;
            missed++;
            // 이름을 아는 루틴 · Task 면 그 줄에 놓침 +1
            String key = m.routineId() != null ? "R" + m.routineId() : keyByTask.get(m.taskId());
            if (key != null && info.containsKey(key)) counts.get(key)[2]++;
        }
        int total = onTime + late + missed;
        Integer pOn = null, pLate = null, pMissed = null;
        if (total > 0) {
            pOn = (int) Math.round(100.0 * onTime / total);
            pLate = (int) Math.round(100.0 * late / total);
            pMissed = Math.max(0, 100 - pOn - pLate); // 합이 100 이 되도록 나머지
        }
        return new BlockKeepingStats(total, pOn, pLate, pMissed, frequentMoves(set), items(counts, info));
    }

    static String itemKey(PatternTaskSample t) {
        return t.routineId() != null ? "R" + t.routineId() : "T" + t.displayName();
    }

    /** 많이 한(표본이 많은) 순, 같으면 놓침이 많은 순 */
    static List<BlockKeepingStats.KeepItem> items(Map<String, int[]> counts, Map<String, PatternTaskSample> info) {
        List<BlockKeepingStats.KeepItem> out = new ArrayList<>();
        counts.forEach((key, c) -> {
            PatternTaskSample t = info.get(key);
            out.add(new BlockKeepingStats.KeepItem(key, t.displayName(), t.displayEmoji(), t.goalName(), t.goalColorCode(),
                    t.routineId() != null, c[0], c[1], c[2]));
        });
        out.sort(Comparator.comparingInt(BlockKeepingStats.KeepItem::total).reversed()
                .thenComparing(Comparator.comparingInt(BlockKeepingStats.KeepItem::missed).reversed()));
        return out.size() > MAX_ITEMS ? List.copyOf(out.subList(0, MAX_ITEMS)) : out;
    }

    /**
     * 사용자가 옮긴 기록을 루틴 단위로 묶어 횟수 순 상위 3개.
     * 루틴만 본다 — 일회성 Task 는 옮겨도 다음에 반복되지 않아서, "자주 옮긴다"는 사실로 바꿀 수 있는 게 없다.
     * (루틴은 "이 시간 말고 저 시간에 하더라" → 루틴 시간을 바꾸는 제안으로 이어진다)
     * 이름을 알 수 없는(기간 밖) 기록은 건너뛴다.
     */
    static List<BlockKeepingStats.FrequentMove> frequentMoves(PatternSampleSet set) {
        Map<Long, PatternTaskSample> byRoutine = new HashMap<>();
        set.tasks().forEach(t -> { if (t.routineId() != null) byRoutine.putIfAbsent(t.routineId(), t); });

        Map<String, List<TaskScheduleLog>> groups = new LinkedHashMap<>();
        Map<String, PatternTaskSample> groupInfo = new HashMap<>();
        for (TaskScheduleLog l : set.scheduleLogs()) {
            if (l.getAction() != TaskScheduleAction.USER_MOVE) continue;
            if (l.getFromStartAt() == null || l.getToStartAt() == null) continue;
            if (!set.range().contains(l.getFromDate())) continue;
            if (l.getRoutineId() == null) continue; // 일회성 Task 는 제외
            PatternTaskSample info = byRoutine.get(l.getRoutineId());
            if (info == null) continue;
            String key = "R" + l.getRoutineId();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(l);
            groupInfo.putIfAbsent(key, info);
        }

        List<BlockKeepingStats.FrequentMove> result = new ArrayList<>();
        groups.forEach((key, logs) -> {
            if (logs.size() < MIN_MOVE_COUNT) return;
            PatternTaskSample info = groupInfo.get(key);
            long dayChanged = logs.stream().filter(TaskScheduleLog::isDayChanged).count();
            boolean dayChange = dayChanged * 2 >= logs.size();
            String from, to, desc;
            if (dayChange) {
                DayOfWeek fromDay = mode(logs, l -> l.getFromDate().getDayOfWeek());
                DayOfWeek toDay = mode(logs, l -> l.getToDate().getDayOfWeek());
                from = TimeLabels.dayShort(fromDay);
                to = TimeLabels.dayShort(toDay);
                desc = TimeLabels.dayLong(fromDay) + " → " + TimeLabels.dayLong(toDay);
            } else {
                from = mode(logs, l -> TimeLabels.hhmm10(l.getFromStartAt()));
                to = mode(logs, l -> TimeLabels.hhmm10(l.getToStartAt()));
                int fromHour = Integer.parseInt(from.substring(0, 2));
                int toHour = Integer.parseInt(to.substring(0, 2));
                String group = groupLabel(logs);
                desc = (group != null ? group + " " : "") + TimeLabels.slotName(fromHour) + " → " + TimeLabels.slotName(toHour);
            }
            result.add(new BlockKeepingStats.FrequentMove(info.routineId(), info.displayName(), info.displayEmoji(),
                    from, to, dayChange, desc, logs.size()));
        });
        result.sort(Comparator.comparingInt(BlockKeepingStats.FrequentMove::count).reversed());
        return result.size() > MAX_MOVES ? List.copyOf(result.subList(0, MAX_MOVES)) : result;
    }

    /** 옮기기 전 날짜가 모두 평일이면 "평일", 모두 주말이면 "주말" */
    private static String groupLabel(List<TaskScheduleLog> logs) {
        boolean allWeekday = logs.stream().allMatch(l -> !DayGroup.isWeekend(l.getFromDate().getDayOfWeek()));
        boolean allWeekend = logs.stream().allMatch(l -> DayGroup.isWeekend(l.getFromDate().getDayOfWeek()));
        if (allWeekday) return DayGroup.WEEKDAY.label();
        if (allWeekend) return DayGroup.WEEKEND.label();
        return null;
    }

    /** 최빈값 (같으면 먼저 나온 값) */
    static <T> T mode(List<TaskScheduleLog> logs, Function<TaskScheduleLog, T> f) {
        Map<T, Integer> counts = new LinkedHashMap<>();
        logs.forEach(l -> counts.merge(f.apply(l), 1, Integer::sum));
        T best = null;
        int bestCount = 0;
        for (Map.Entry<T, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }
}
