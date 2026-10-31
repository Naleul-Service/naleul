package com.naleul.naleul.domain.pattern.support;

import com.naleul.naleul.domain.pattern.enums.PatternSuggestionType;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class SuggestionKeyTest {

    @Test
    void 시간_변경_key() {
        SuggestionKey k = SuggestionKey.parse("ROUTINE_TIME:31:12:40").orElseThrow();
        assertThat(k.type()).isEqualTo(PatternSuggestionType.ROUTINE_TIME);
        assertThat(k.routineId()).isEqualTo(31L);
        assertThat(k.time()).isEqualTo(LocalTime.of(12, 40));
    }

    @Test
    void 요일_변경_key() {
        SuggestionKey k = SuggestionKey.parse("ROUTINE_DAY:44:THURSDAY:FRIDAY").orElseThrow();
        assertThat(k.type()).isEqualTo(PatternSuggestionType.ROUTINE_DAY);
        assertThat(k.fromDay()).isEqualTo(DayOfWeek.THURSDAY);
        assertThat(k.toDay()).isEqualTo(DayOfWeek.FRIDAY);
    }

    @Test
    void 잘못된_key는_빈_값() {
        assertThat(SuggestionKey.parse(null).isPresent()).isFalse();
        assertThat(SuggestionKey.parse("ROUTINE_TIME:abc:12:40").isPresent()).isFalse();
        assertThat(SuggestionKey.parse("ROUTINE_TIME:31:25:00").isPresent()).isFalse();
        assertThat(SuggestionKey.parse("ROUTINE_DAY:44:THURSDAY:THURSDAY").isPresent()).isFalse();
        assertThat(SuggestionKey.parse("ROUTINE_DAY:44:THU:FRI").isPresent()).isFalse();
        assertThat(SuggestionKey.parse("DROP_TABLE:1:2:3").isPresent()).isFalse();
    }
}
