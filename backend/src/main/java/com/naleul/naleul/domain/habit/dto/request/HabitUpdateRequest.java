package com.naleul.naleul.domain.habit.dto.request;

import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * 습관 수정 (V52) — 보낸 값만 바꾼다.
 * - emoji 를 "" 로 보내면 지운다
 * - repeatDays 를 [] 로 보내면 매일
 * - clearRemindTime = true 면 알림 시각을 지운다
 */
public record HabitUpdateRequest(
        @Size(max = 30, message = "습관 이름은 30자 이하로 입력해 주세요.")
        String name,

        @Size(max = 16)
        String emoji,

        List<DayOfWeek> repeatDays,

        LocalTime remindTime,

        Boolean clearRemindTime,

        /** 하는 시간대 (V56) — 둘 다 보내면 바꾸고, clearTimeRange = true 면 지운다 */
        LocalTime startTime,
        LocalTime endTime,
        Boolean clearTimeRange
) {
    public HabitUpdateRequest(String name, String emoji, List<DayOfWeek> repeatDays, LocalTime remindTime,
                              Boolean clearRemindTime) {
        this(name, emoji, repeatDays, remindTime, clearRemindTime, null, null, null);
    }
}
