package com.naleul.naleul.domain.lifePattern.dto.response;

import com.naleul.naleul.domain.lifePattern.enums.LifePatternType;
import com.naleul.naleul.domain.lifePattern.support.FixedBlock;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 화면에 그릴 고정 블록 하나.
 * targetDate: 그날만 변경 API(/life-patterns/{id}/overrides/{targetDate})에 넘길 날짜.
 *             자정을 넘긴 수면의 다음 날 부분을 드래그해도 targetDate 는 전날이다.
 */
public record FixedBlockResponse(
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
    public static FixedBlockResponse from(FixedBlock b) {
        return new FixedBlockResponse(b.lifePatternId(), b.patternType(), b.title(), b.emoji(),
                b.targetDate(), b.start(), b.end(), b.overridden(), b.clippedFromPreviousDay());
    }
}
