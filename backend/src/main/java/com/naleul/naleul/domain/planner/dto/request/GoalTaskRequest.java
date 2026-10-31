package com.naleul.naleul.domain.planner.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 목표 상세에서 Task 추가·수정 (폼 전체를 보냄).
 *
 * 예) 시간 미정:  { "taskName": "영단어 50개", "generalCategoryId": 3, "date": "2026-10-07", "durationMinutes": 30 }
 *     시간 지정:  { ..., "date": "2026-10-07", "startTime": "20:00", "endTime": "21:00" }
 *
 * - startTime/endTime 은 둘 다 있거나 둘 다 없어야 한다. endTime ≤ startTime 이면 다음 날까지.
 * - 시간이 없으면 그날 "시간 미정"으로 두고 자동 배치가 durationMinutes(없으면 30분)로 시간을 정한다.
 * - milestoneId · emoji · dueDate 는 null 이면 비운다.
 */
public record GoalTaskRequest(
        @NotBlank(message = "Task 이름은 필수입니다.")
        @Size(max = 100, message = "Task 이름은 100자 이하로 입력해주세요.")
        String taskName,

        @Size(max = 16)
        String emoji,

        /** 영역(세부 목표). 비우면 목표의 "기타 할 일"에 들어간다 */
        Long generalCategoryId,

        Long milestoneId,

        @NotNull(message = "날짜는 필수입니다.")
        LocalDate date,

        LocalTime startTime,
        LocalTime endTime,

        @Min(value = 5, message = "소요 시간은 5분 이상이어야 합니다.")
        @Max(value = 1440, message = "소요 시간은 24시간 이하여야 합니다.")
        Integer durationMinutes,

        LocalDate dueDate
) {
    public boolean hasTime() {
        return startTime != null && endTime != null;
    }
}
