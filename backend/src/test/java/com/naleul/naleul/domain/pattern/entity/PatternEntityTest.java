package com.naleul.naleul.domain.pattern.entity;

import com.naleul.naleul.domain.pattern.enums.PatternSuggestionActionType;
import com.naleul.naleul.domain.pattern.enums.PatternSuggestionType;
import com.naleul.naleul.domain.pattern.enums.TaskScheduleAction;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatternEntityTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 22, 0);

    @Test
    void 일정_기록은_시각만_주면_날짜를_시각에서_채운다() {
        TaskScheduleLog log = TaskScheduleLog.of(1L, 10L, 3L, TaskScheduleAction.USER_MOVE,
                null, LocalDateTime.of(2026, 11, 19, 8, 0),
                null, LocalDateTime.of(2026, 11, 20, 12, 40), NOW);

        assertThat(log.getFromDate()).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(log.getToDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(log.isDayChanged()).isTrue();
        assertThat(log.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void 이월_기록은_원래_날짜만_있어도_된다() {
        TaskScheduleLog log = TaskScheduleLog.of(1L, 10L, null, TaskScheduleAction.CARRY_OVER,
                LocalDate.of(2026, 11, 18), null, LocalDate.of(2026, 11, 19), null, NOW);

        assertThat(log.getFromDate()).isEqualTo(LocalDate.of(2026, 11, 18));
        assertThat(log.getFromStartAt()).isNull();
        assertThat(log.isDayChanged()).isTrue();
    }

    @Test
    void 일정_기록_필수값이_없으면_예외() {
        assertThatThrownBy(() -> TaskScheduleLog.of(1L, null, null, TaskScheduleAction.USER_MOVE,
                null, null, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 브레인덤프_기록_개수는_음수가_되지_않는다() {
        BrainDumpLog log = BrainDumpLog.of(1L, -1, 2, NOW);
        assertThat(log.getItemCount()).isZero();
        assertThat(log.getTempGoalCount()).isEqualTo(2);
    }

    @Test
    void 제안_반영과_미루기() {
        PatternSuggestionAction applied = PatternSuggestionAction.applied(1L, "ROUTINE_TIME:31:12:40",
                PatternSuggestionType.ROUTINE_TIME, 31L, "{\"startTime\":\"08:00\"}", "{\"startTime\":\"12:40\"}", NOW);
        PatternSuggestionAction dismissed = PatternSuggestionAction.dismissed(1L, "ROUTINE_DAY:44:THURSDAY:FRIDAY",
                PatternSuggestionType.ROUTINE_DAY, 44L, NOW);

        assertThat(applied.getAction()).isEqualTo(PatternSuggestionActionType.APPLIED);
        assertThat(applied.getAfterJson()).contains("12:40");
        assertThat(dismissed.getAction()).isEqualTo(PatternSuggestionActionType.DISMISSED);
        assertThat(dismissed.getBeforeJson()).isNull();
    }

    @Test
    void 제안_key가_100자를_넘으면_예외() {
        String longKey = "K".repeat(101);
        assertThatThrownBy(() -> PatternSuggestionAction.dismissed(1L, longKey,
                PatternSuggestionType.ROUTINE_DAY, 1L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
