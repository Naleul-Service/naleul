package com.naleul.naleul.domain.goalCreation.support;

import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.goalCreation.dto.response.GoalSummaryResponse;
import com.naleul.naleul.domain.goalCreation.dto.slot.*;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 슬롯 → 요약 문구.
 * 예) metricText "78kg → 68kg", deadlineText "2027년 3월 31일까지 (약 6개월)", preferenceText "월·수·금 · 19:00~22:00"
 */
@Component
public class GoalSummaryBuilder {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<Weekday, String> DAY_KO = Map.of(
            Weekday.MON, "월", Weekday.TUE, "화", Weekday.WED, "수", Weekday.THU, "목",
            Weekday.FRI, "금", Weekday.SAT, "토", Weekday.SUN, "일");

    public GoalSummaryResponse build(GoalSlots slots, PlanningStyle planningStyle, LocalDate today) {
        return new GoalSummaryResponse(
                slots.goalStatement().isFilled() ? slots.goalStatement().value() : null,
                slots.metric().isFilled() ? metricText(slots.metric().value()) : null,
                slots.motivation().isFilled() ? slots.motivation().value() : null,
                slots.deadline().isFilled() ? deadlineText(slots.deadline().value(), today) : null,
                slots.practicePreference().isFilled() ? preferenceText(slots.practicePreference().value()) : null,
                planningStyle,
                slots.approach().isFilled() ? slots.approach().value() : null
        );
    }

    private String metricText(MetricSlot m) {
        if (m == null) return null;
        String unit = m.unit() != null ? m.unit() : "";
        if (m.currentValue() == null && m.targetValue() == null) return null;
        if (m.currentValue() == null) return "목표 " + num(m.targetValue()) + unit;
        if (m.targetValue() == null) return "현재 " + num(m.currentValue()) + unit;
        return num(m.currentValue()) + unit + " → " + num(m.targetValue()) + unit;
    }

    /** 78.00 → "78", 78.50 → "78.5" */
    private String num(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private String deadlineText(DeadlineSlot d, LocalDate today) {
        if (d == null || d.endDate() == null) return null;
        LocalDate from = d.startDate() != null && d.startDate().isAfter(today) ? d.startDate() : today;
        long months = ChronoUnit.MONTHS.between(from, d.endDate());
        String period = months >= 1 ? " (약 " + months + "개월)" : " (" + ChronoUnit.DAYS.between(from, d.endDate()) + "일)";
        String flexible = d.isFlexible() ? " · AI 제안" : "";
        String start = from.equals(today) ? "" : from.format(DATE) + "부터 ";
        return start + d.endDate().format(DATE) + "까지" + period + flexible;
    }

    private String preferenceText(PracticePreferenceSlot p) {
        if (p == null) return null;
        List<String> parts = new ArrayList<>();
        if (p.preferredDays() != null && !p.preferredDays().isEmpty()) {
            parts.add(p.preferredDays().stream().sorted().map(DAY_KO::get).collect(Collectors.joining("·")));
        }
        if (p.preferredTimeRanges() != null && !p.preferredTimeRanges().isEmpty()) {
            parts.add(p.preferredTimeRanges().stream()
                    .map(r -> r.start().format(TIME) + "~" + r.end().format(TIME))
                    .collect(Collectors.joining(", ")));
        }
        if (parts.isEmpty() && p.workStyleAnswer() != null) {
            parts.add(p.workStyleAnswer());
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }
}
