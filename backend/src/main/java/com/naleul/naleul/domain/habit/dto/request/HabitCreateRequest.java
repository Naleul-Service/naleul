package com.naleul.naleul.domain.habit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * 습관 추가 (V52)
 * 예) { "name": "영양제 먹기", "emoji": "💊", "repeatDays": [], "remindTime": "08:30" }
 * - repeatDays 를 비우면(또는 7일 전부) 매일
 * - remindTime 은 선택 — 정하면 그 시각에 아직 안 했을 때 푸시
 */
public record HabitCreateRequest(
        @NotBlank(message = "습관 이름을 입력해 주세요.")
        @Size(max = 30, message = "습관 이름은 30자 이하로 입력해 주세요.")
        String name,

        @Size(max = 16)
        String emoji,

        List<DayOfWeek> repeatDays,

        LocalTime remindTime,

        /** 하는 시간대 (선택, V56) — 예) 샤워 20:00 ~ 20:30. 캘린더에 블록으로 보여요 */
        LocalTime startTime,
        LocalTime endTime
) {
    public HabitCreateRequest(String name, String emoji, List<DayOfWeek> repeatDays, LocalTime remindTime) {
        this(name, emoji, repeatDays, remindTime, null, null);
    }
}
