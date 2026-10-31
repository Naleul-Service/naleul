package com.naleul.naleul.domain.brainDump.support;

import com.naleul.naleul.domain.brainDump.enums.DateType;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import com.naleul.naleul.domain.user.enums.PlanningStyle;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 확정 단계의 날짜·시간 규칙 (순수 계산 → 단위 테스트) */
public final class BrainDumpRules {

    private BrainDumpRules() {
    }

    /**
     * 시간 없는 Task 를 처음 시도할 날짜 (명세 6-4 성향별 규칙).
     *  ON  : 그날 (지났으면 오늘)
     *  DUE : PLANNER(또는 성향 없음) → 오늘부터 마감일 쪽으로
     *        SPONTANEOUS             → 마감 전날부터 (마감이 오늘·내일이면 오늘)
     * 첫 날에 자리가 없으면 배치 서비스가 마감일까지 다음 날로 넘겨본다.
     */
    public static LocalDate firstTryDate(DateType type, LocalDate scheduledDate, LocalDate dueDate,
                                         PlanningStyle style, LocalDate today) {
        if (type == DateType.DUE && dueDate != null) {
            LocalDate due = dueDate.isBefore(today) ? today : dueDate;
            if (style == PlanningStyle.SPONTANEOUS) {
                LocalDate dayBefore = due.minusDays(1);
                return dayBefore.isBefore(today) ? today : dayBefore;
            }
            return today;
        }
        LocalDate d = scheduledDate != null ? scheduledDate : today;
        return d.isBefore(today) ? today : d;
    }

    /**
     * 사용자가 정한 시각의 구간. 종료가 없으면 소요 시간으로, 종료가 시작보다 이르면 다음 날로 넘긴다 (23:00~01:00).
     */
    public static DateTimeRange explicitRange(LocalDate date, LocalTime start, LocalTime end, int minutes) {
        LocalDateTime s = date.atTime(start);
        LocalDateTime e;
        if (end == null) e = s.plusMinutes(Math.max(minutes, 5));
        else if (end.isAfter(start)) e = date.atTime(end);
        else e = date.plusDays(1).atTime(end);
        return new DateTimeRange(s, e);
    }
}
