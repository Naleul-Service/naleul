package com.naleul.naleul.domain.lifePattern.support;

import com.naleul.naleul.domain.lifePattern.enums.LifePatternType;
import com.naleul.naleul.domain.planner.support.DateTimeRange;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 특정 날짜에 실제로 그려지는 고정 블록 (패턴 + 그날 예외를 합친 결과, DB에 저장하지 않음).
 *
 * @param targetDate             블록이 시작한 날짜. 그날만 변경(Override) API에 이 날짜를 넘긴다
 * @param start, end             실제 구간 (자정을 넘기면 end 는 다음 날)
 * @param overridden             그날만 변경된 블록인지
 * @param clippedFromPreviousDay 전날 시작해서 이날 00:00 부터 잘라 보여주는 블록인지 (예: 수면 23:00~07:00 의 다음 날 부분)
 */
public record FixedBlock(
        Long lifePatternId,
        LifePatternType patternType,
        String title,
        String emoji,
        LocalDate targetDate,
        LocalDateTime start,
        LocalDateTime end,
        boolean overridden,
        boolean clippedFromPreviousDay
) {
    public DateTimeRange range() {
        return new DateTimeRange(start, end);
    }

    /** 이 날짜 화면에 보여줄 부분만 잘라낸 사본 */
    FixedBlock clipTo(LocalDate displayDate) {
        LocalDateTime dayStart = displayDate.atStartOfDay();
        LocalDateTime dayEnd = displayDate.plusDays(1).atStartOfDay();
        LocalDateTime s = start.isBefore(dayStart) ? dayStart : start;
        LocalDateTime e = end.isAfter(dayEnd) ? dayEnd : end;
        boolean clipped = start.isBefore(dayStart);
        return new FixedBlock(lifePatternId, patternType, title, emoji, targetDate, s, e, overridden, clipped);
    }
}
