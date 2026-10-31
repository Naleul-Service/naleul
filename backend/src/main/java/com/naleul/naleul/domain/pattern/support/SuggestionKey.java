package com.naleul.naleul.domain.pattern.support;

import com.naleul.naleul.domain.pattern.enums.PatternSuggestionType;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * 제안 key 해석 (명세 4 · 7-2).
 *   ROUTINE_TIME:{routineId}:{HH}:{mm}          예) ROUTINE_TIME:31:12:40
 *   ROUTINE_DAY:{routineId}:{FROM_DAY}:{TO_DAY}  예) ROUTINE_DAY:44:THURSDAY:FRIDAY
 *
 * key 안에 바꿀 내용이 모두 들어 있어서, 리포트 캐시가 지워진 뒤에도 반영할 수 있다.
 * (대신 반영 전에 루틴의 현재 상태로 다시 검증한다)
 */
public record SuggestionKey(String raw, PatternSuggestionType type, Long routineId,
                            LocalTime time, DayOfWeek fromDay, DayOfWeek toDay) {

    public static Optional<SuggestionKey> parse(String raw) {
        if (raw == null || raw.length() > 100) return Optional.empty();
        String[] p = raw.split(":");
        try {
            if (p.length == 4 && p[0].equals(PatternSuggestionType.ROUTINE_TIME.name())) {
                LocalTime t = LocalTime.parse(p[2] + ":" + p[3]);
                return Optional.of(new SuggestionKey(raw, PatternSuggestionType.ROUTINE_TIME, Long.parseLong(p[1]), t, null, null));
            }
            if (p.length == 4 && p[0].equals(PatternSuggestionType.ROUTINE_DAY.name())) {
                DayOfWeek from = DayOfWeek.valueOf(p[2]), to = DayOfWeek.valueOf(p[3]);
                if (from == to) return Optional.empty();
                return Optional.of(new SuggestionKey(raw, PatternSuggestionType.ROUTINE_DAY, Long.parseLong(p[1]), null, from, to));
            }
        } catch (NumberFormatException | DateTimeParseException e) {
            return Optional.empty();
        } catch (IllegalArgumentException e) { // DayOfWeek.valueOf
            return Optional.empty();
        }
        return Optional.empty();
    }
}
