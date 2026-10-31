package com.naleul.naleul.domain.pattern.calculator;

import com.naleul.naleul.domain.pattern.enums.PatternPeriod;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 분석 기간 [start, end] (양 끝 포함, 명세 4 · 1-2).
 * end 는 항상 어제 — 오늘은 아직 진행 중이라 "놓침"을 판단할 수 없다.
 */
public record PatternRange(LocalDate start, LocalDate end) {

    public PatternRange {
        if (start == null || end == null || start.isAfter(end)) {
            throw new IllegalArgumentException("잘못된 기간: " + start + " ~ " + end);
        }
    }

    /**
     * @param firstDate 사용자의 첫 기록 날짜 (없으면 null). ALL 에서만 쓴다.
     * @return 기록이 없어 ALL 기간을 만들 수 없으면 RECENT_4W 와 같은 범위
     */
    public static PatternRange of(PatternPeriod period, LocalDate today, LocalDate firstDate) {
        LocalDate end = today.minusDays(1);
        LocalDate recentStart = end.minusDays(PatternPeriod.RECENT_DAYS - 1L);
        if (period == PatternPeriod.RECENT_4W || firstDate == null) {
            return new PatternRange(recentStart, end);
        }
        LocalDate maxStart = end.minusDays(PatternPeriod.ALL_MAX_DAYS - 1L);
        LocalDate start = firstDate.isBefore(maxStart) ? maxStart : firstDate;
        if (start.isAfter(end)) start = end; // 어제 처음 기록한 사용자 → 하루짜리 기간
        return new PatternRange(start, end);
    }

    public long days() {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    /** 주 수 (주 평균 계산용, 최소 1주) */
    public double weeks() {
        return Math.max(days() / 7.0, 1.0);
    }

    public boolean contains(LocalDate d) {
        return d != null && !d.isBefore(start) && !d.isAfter(end);
    }
}
