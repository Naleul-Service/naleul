package com.naleul.naleul.domain.pattern.dto;

import com.naleul.naleul.domain.pattern.calculator.*;
import com.naleul.naleul.domain.pattern.enums.PatternAiStatus;
import com.naleul.naleul.domain.pattern.enums.PatternPeriod;
import com.naleul.naleul.domain.pattern.enums.PatternSuggestionType;
import com.naleul.naleul.domain.pattern.enums.StyleRhythm;
import com.naleul.naleul.domain.pattern.enums.StyleTimeStrength;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/v1/patterns 응답 (명세 4 · 7-1).
 * 카드 계산 결과(calculator 의 record)를 그대로 담는다 — 카드 하나가 계산에 실패하면 그 필드만 null.
 * 이 객체를 JSON 으로 pattern_report 에 캐시하므로 필드를 바꾸면 그날 캐시는 다시 계산된다(읽기 실패 시 재계산).
 */
public record PatternReportResponse(
        PatternPeriod period,
        Range range,
        int timeBlockCount,
        int evaluatedCount,
        LocalDateTime generatedAt,
        PatternAiStatus aiStatus,
        Style style,
        SummaryStats summary,
        HeatmapStats heatmap,
        Weekday weekday,
        WeeklyTrendStats weekly,
        TimeUsageStats timeUsage,
        BlockKeepingStats blockKeeping,
        RoutineRankStats routines,
        TaskHabitStats taskHabits,
        SuggestionBar suggestion,
        /**
         * 계산에 쓴 기록의 "버전" (Task 수·완료 수·마지막 완료·일정 변경·실제 시간 기록·지금 지난 블록 수를 이은 문자열).
         * 캐시를 읽을 때 지금 값과 다르면 다시 계산한다 — 완료 취소·이동·삭제·실제 시간 수정도 바로 반영되게.
         */
        String dataVersion
) {

    public PatternReportResponse(PatternPeriod period, Range range, int timeBlockCount, int evaluatedCount,
                                 LocalDateTime generatedAt, PatternAiStatus aiStatus, Style style, SummaryStats summary,
                                 HeatmapStats heatmap, Weekday weekday, WeeklyTrendStats weekly, TimeUsageStats timeUsage,
                                 BlockKeepingStats blockKeeping, RoutineRankStats routines, TaskHabitStats taskHabits,
                                 SuggestionBar suggestion) {
        this(period, range, timeBlockCount, evaluatedCount, generatedAt, aiStatus, style, summary, heatmap, weekday,
                weekly, timeUsage, blockKeeping, routines, taskHabits, suggestion, null);
    }

    public PatternReportResponse withDataVersion(String version) {
        return new PatternReportResponse(period, range, timeBlockCount, evaluatedCount, generatedAt, aiStatus, style,
                summary, heatmap, weekday, weekly, timeUsage, blockKeeping, routines, taskHabits, suggestion, version);
    }

    public record Range(LocalDate start, LocalDate end, long days) {
    }

    /**
     * 실행 스타일. 기록이 부족하면 title 등이 null 이고 remainingForStyle 에 "n개 더"가 들어간다.
     */
    public record Style(StyleTimeStrength timeStrength, StyleRhythm rhythm, String title, String highlight,
                        List<Insight> insights, Integer remainingForStyle, Progress progress) {
        public Style(StyleTimeStrength timeStrength, StyleRhythm rhythm, String title, String highlight,
                     List<Insight> insights, Integer remainingForStyle) {
            this(timeStrength, rhythm, title, highlight, insights, remainingForStyle, null);
        }
    }

    /**
     * 스타일을 열기까지의 진행 — "Task 15개 · 4일 더" 진행 바용.
     * remainingTasks/remainingDays 가 둘 다 0 이면 열린 상태.
     */
    public record Progress(int evaluated, int activeDays, int requiredTasks, int requiredDays,
                           int remainingTasks, int remainingDays) {
    }

    /** text 의 **…** 는 강조 */
    public record Insight(String key, String icon, String text) {
    }

    /** 요일별 카드 + 하단 문장 */
    public record Weekday(Integer average, List<WeekdayStats.Day> days, DayOfWeek lowest, String lowestMissed,
                          String insight) {
    }

    /** 하단 제안 바. text 의 **…** 는 강조 */
    public record SuggestionBar(String text, Integer expectedGain, List<SuggestionItem> items) {
    }

    /** before/after: 시간 변경이면 startTime·endTime("HH:mm"), 요일 변경이면 days */
    public record SuggestionItem(String key, PatternSuggestionType type, Long routineId, String routineName,
                                 Change before, Change after) {
    }

    public record Change(String startTime, String endTime, List<DayOfWeek> days) {
    }
}
