package com.naleul.naleul.domain.pattern.enums;

import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;

/**
 * 쿼리 파라미터 문자열 → PatternPeriod.
 * @RequestParam 으로 enum 을 바로 받으면 잘못된 값이 MethodArgumentTypeMismatchException 이 되는데,
 * GlobalExceptionHandler 에 그 처리기가 없어 500 으로 나간다 → 직접 바꿔서 400 으로 응답한다.
 */
public final class PatternPeriodParser {

    private PatternPeriodParser() {
    }

    public static PatternPeriod parse(String value) {
        if (value == null || value.isBlank()) return PatternPeriod.RECENT_4W;
        try {
            return PatternPeriod.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.PATTERN_INVALID_PERIOD);
        }
    }
}
