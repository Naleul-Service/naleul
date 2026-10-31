package com.naleul.naleul.domain.pattern.calculator;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

/**
 * 제안대로 바꾸면 앞으로 2주 동안 고정 시간·잠긴 일정과 얼마나 겹치는지.
 * 계산기를 DB 없이 테스트하려고 인터페이스로 뺐다 (실제 구현은 PatternConflictCheckerFactory).
 */
@FunctionalInterface
public interface ConflictChecker {

    /** @return 바뀐 요일·시각에 해당하는 날 중 겹치는 날의 비율 (0.0 ~ 1.0) */
    double conflictRatio(RoutineInfo routine, Set<DayOfWeek> days, LocalTime start, LocalTime end);

    ConflictChecker NONE = (r, d, s, e) -> 0;
}
