package com.naleul.naleul.domain.lifePattern.dto.response;

import com.naleul.naleul.domain.lifePattern.entity.LifePattern;
import com.naleul.naleul.domain.lifePattern.enums.LifePatternType;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record LifePatternResponse(
        Long lifePatternId,
        LifePatternType patternType,
        String title,
        String emoji,
        LocalTime startTime,
        LocalTime endTime,
        List<DayOfWeek> days,
        boolean everyDay,
        boolean crossesMidnight,
        int sortOrder,
        /** 요일별로 다른 시간 (없으면 모든 요일이 기본 시간) */
        List<LifePatternDayTimeResponse> dayTimes,
        /** 이날만 패턴이면 그 날짜 (매주 반복이면 null) */
        java.time.LocalDate onlyDate
) {
    public static LifePatternResponse from(LifePattern p) {
        return new LifePatternResponse(
                p.getLifePatternId(), p.getPatternType(), p.getTitle(), p.getEmoji(),
                p.getStartTime(), p.getEndTime(),
                p.getDays().stream().sorted().toList(),
                p.isEveryDay(), p.crossesMidnight(), p.getSortOrder(),
                p.getDayTimes().stream()
                        .sorted(java.util.Comparator.comparing(dt -> dt.getDayOfWeek()))
                        .map(LifePatternDayTimeResponse::from)
                        .toList(),
                p.getOnlyDate());
    }
}
