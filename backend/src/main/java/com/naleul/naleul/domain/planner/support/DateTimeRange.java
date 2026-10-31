package com.naleul.naleul.domain.planner.support;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * [start, end) 반열린 구간. 고정 블록·Task 블록의 겹침 판정에 공통으로 쓴다.
 * 10:00~11:00 과 11:00~12:00 은 끝과 시작이 같아서 겹치지 않는 것으로 본다.
 */
public record DateTimeRange(LocalDateTime start, LocalDateTime end) {

    public DateTimeRange {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("end must be after start: " + start + " ~ " + end);
        }
    }

    public boolean overlaps(DateTimeRange other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public boolean contains(LocalDateTime t) {
        return !t.isBefore(start) && t.isBefore(end);
    }

    public long minutes() {
        return Duration.between(start, end).toMinutes();
    }
}
