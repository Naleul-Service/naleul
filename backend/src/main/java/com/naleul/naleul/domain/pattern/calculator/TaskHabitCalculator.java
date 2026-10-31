package com.naleul.naleul.domain.pattern.calculator;

import com.naleul.naleul.domain.pattern.entity.BrainDumpLog;

import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Task 처리 습관 (명세 4 · 2-8) */
public final class TaskHabitCalculator {

    static final int MIN_DEADLINE = 5;
    static final int MIN_POSTPONE = 5;
    static final int MIN_BRAIN_DUMP = 2;
    static final int MIN_BRAIN_DUMP_USUAL = 3;

    private TaskHabitCalculator() {
    }

    public static TaskHabitStats calculate(PatternSampleSet set) {
        List<String> insufficient = new ArrayList<>();
        List<PatternTaskSample> oneOff = set.tasks().stream().filter(t -> !t.isRoutine()).toList();

        // 마감 대비 완료: (마감일 − 완료일) 평균
        Double lead = null;
        String leadLabel = null;
        List<Long> leads = new ArrayList<>();
        for (PatternTaskSample t : oneOff) {
            if (t.completed() && t.dueDate() != null && t.completedAt() != null) {
                leads.add(ChronoUnit.DAYS.between(t.completedAt().toLocalDate(), t.dueDate()));
            }
        }
        if (leads.size() >= MIN_DEADLINE) {
            lead = Math.round(leads.stream().mapToLong(Long::longValue).average().orElse(0) * 10) / 10.0;
            leadLabel = lead >= 1 ? "여유 있게 끝내는 편" : lead >= 0 ? "마감에 맞춰 끝내는 편" : "마감을 넘기는 편";
        } else {
            insufficient.add("deadlineLeadDays");
        }

        // 미룬 Task
        Integer postponed = null;
        String topGoal = null;
        if (oneOff.size() >= MIN_POSTPONE) {
            List<PatternTaskSample> carried = oneOff.stream().filter(t -> t.carryOverCount() > 0).toList();
            postponed = (int) Math.round(100.0 * carried.size() / oneOff.size());
            Map<String, Integer> byGoal = new LinkedHashMap<>();
            carried.forEach(t -> { if (t.goalName() != null) byGoal.merge(t.goalName(), 1, Integer::sum); });
            topGoal = byGoal.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        } else {
            insufficient.add("postponedRate");
        }

        // Brain dump
        List<BrainDumpLog> logs = set.brainDumpLogs();
        Double perWeek = null, avgItems = null;
        if (logs.size() >= MIN_BRAIN_DUMP) {
            perWeek = Math.round(logs.size() / set.range().weeks() * 10) / 10.0;
            avgItems = Math.round(logs.stream().mapToInt(BrainDumpLog::getItemCount).average().orElse(0) * 10) / 10.0;
        } else {
            insufficient.add("brainDumpPerWeek");
        }
        DayOfWeek usualDay = null;
        String usualLabel = null, usualRange = null;
        if (logs.size() >= MIN_BRAIN_DUMP_USUAL) {
            Map<DayOfWeek, List<BrainDumpLog>> byDay = new EnumMap<>(DayOfWeek.class);
            logs.forEach(l -> byDay.computeIfAbsent(l.getConfirmedAt().getDayOfWeek(), k -> new ArrayList<>()).add(l));
            usualDay = byDay.entrySet().stream()
                    .max(Comparator.comparingInt((Map.Entry<DayOfWeek, List<BrainDumpLog>> e) -> e.getValue().size()))
                    .map(Map.Entry::getKey).orElse(null);
            int[] hourCount = new int[24];
            byDay.get(usualDay).forEach(l -> hourCount[l.getConfirmedAt().getHour()]++);
            int bestStart = 0, best = -1;
            for (int h = 0; h < 23; h++) {
                int c = hourCount[h] + hourCount[h + 1];
                if (c > best) {
                    best = c;
                    bestStart = h;
                }
            }
            usualLabel = TimeLabels.dayLong(usualDay) + " " + TimeLabels.partOfDay(bestStart);
            usualRange = bestStart + "–" + (bestStart + 2) + "시";
        } else {
            insufficient.add("brainDumpUsual");
        }

        return new TaskHabitStats(oneOff.size(), lead, leadLabel, postponed, topGoal,
                perWeek, avgItems, usualDay, usualLabel, usualRange, List.copyOf(insufficient));
    }
}
