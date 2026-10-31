package com.naleul.naleul.domain.lifePattern.dto.response;

import com.naleul.naleul.domain.planner.enums.ChangeScope;

import java.time.LocalDate;
import java.util.List;

/**
 * @param changedDates 시간이 바뀐 날짜들 (FOLLOWING 이면 기준 날짜 하나 — 이후는 기본값으로 따라감)
 * @param pattern      바뀐 뒤의 패턴 (FOLLOWING 이면 새 기본값)
 */
public record LifePatternScopedChangeResponse(
        ChangeScope scope,
        List<LocalDate> changedDates,
        LifePatternResponse pattern
) {
}
