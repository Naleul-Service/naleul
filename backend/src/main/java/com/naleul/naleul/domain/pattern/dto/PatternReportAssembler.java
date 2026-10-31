package com.naleul.naleul.domain.pattern.dto;

import com.naleul.naleul.domain.pattern.calculator.*;
import com.naleul.naleul.domain.pattern.enums.PatternAiStatus;
import com.naleul.naleul.domain.pattern.enums.PatternPeriod;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 계산 결과 → API 응답 */
public final class PatternReportAssembler {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private PatternReportAssembler() {
    }

    public static PatternReportResponse assemble(PatternPeriod period, PatternStats stats, StyleResult style,
                                                 SuggestionResult suggestion, PatternAiStatus aiStatus,
                                                 LocalDateTime now) {
        PatternRange r = stats.range();
        return new PatternReportResponse(
                period,
                new PatternReportResponse.Range(r.start(), r.end(), r.days()),
                stats.timeBlockCount(),
                stats.evaluatedCount(),
                now,
                aiStatus,
                style(style),
                stats.summary(),
                stats.heatmap(),
                weekday(stats.weekday()),
                stats.weekly(),
                stats.timeUsage(),
                stats.blockKeeping(),
                stats.routines(),
                stats.taskHabits(),
                suggestion(suggestion)
        );
    }

    static PatternReportResponse.Style style(StyleResult s) {
        if (s == null) return null;
        List<PatternReportResponse.Insight> insights = s.insights().stream()
                .map(i -> new PatternReportResponse.Insight(i.key(), i.icon(), i.text()))
                .toList();
        StyleResult.Progress p = s.progress();
        return new PatternReportResponse.Style(s.timeStrength(), s.rhythm(), s.title(), s.highlight(),
                insights, s.remaining(),
                p == null ? null : new PatternReportResponse.Progress(p.evaluated(), p.activeDays(),
                        p.requiredTasks(), p.requiredDays(), p.remainingTasks(), p.remainingDays()));
    }

    /** "목요일 실행률이 가장 낮아요. 목요일엔 **근력 운동**을 가장 자주 놓쳤어요." */
    static PatternReportResponse.Weekday weekday(WeekdayStats w) {
        if (w == null) return null;
        String insight = null;
        if (w.lowest() != null) {
            String day = TimeLabels.dayLong(w.lowest());
            insight = day + " 실행률이 가장 낮아요.";
            if (w.lowestMissed() != null) {
                String withJosa = TimeLabels.withEulReul(w.lowestMissed());
                String josa = withJosa.substring(w.lowestMissed().length());
                insight += " " + day + "엔 **" + w.lowestMissed() + "**" + josa + " 가장 자주 놓쳤어요.";
            }
        }
        return new PatternReportResponse.Weekday(w.average(), w.days(), w.lowest(), w.lowestMissed(), insight);
    }

    static PatternReportResponse.SuggestionBar suggestion(SuggestionResult s) {
        if (s == null || s.items().isEmpty()) return null;
        List<PatternReportResponse.SuggestionItem> items = s.items().stream()
                .map(i -> new PatternReportResponse.SuggestionItem(i.key(), i.type(), i.routineId(), i.routineName(),
                        new PatternReportResponse.Change(fmt(i.beforeStart()), fmt(i.beforeEnd()), i.beforeDays()),
                        new PatternReportResponse.Change(fmt(i.afterStart()), fmt(i.afterEnd()), i.afterDays())))
                .toList();
        return new PatternReportResponse.SuggestionBar(s.text(), s.expectedGain(), items);
    }

    private static String fmt(LocalTime t) {
        return t != null ? t.format(HHMM) : null;
    }
}
