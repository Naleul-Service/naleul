package com.naleul.naleul.domain.scheduleEdit.dto;

import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 변경 하나. AI 가 만들고(미리보기), 사용자가 고른 것만 다시 보내 적용한다.
 *
 * @param targetId        MOVE_ROUTINE: 루틴 id · MOVE_TASK: Task id
 * @param days            MOVE_ROUTINE: 새 반복 요일 ["MON","WED"] (안 바꾸면 null)
 * @param startTime       새 시작 시각 "HH:mm" (MOVE_ROUTINE 은 안 바꾸면 null, MOVE_TASK 는 필수)
 * @param durationMinutes 새 길이(분). 없으면 지금 길이 그대로
 * @param date            MOVE_TASK: 옮길 날 (없으면 지금 날 그대로)
 * @param reason          AI 가 이렇게 옮기는 이유 한 문장 (2026-10, 화면 오른쪽 패널에 보여줘요 · 적용할 땐 쓰지 않아요)
 */
public record ScheduleEditOp(
        @NotNull ScheduleEditOpType type,
        @NotNull Long targetId,
        List<Weekday> days,
        LocalTime startTime,
        Integer durationMinutes,
        LocalDate date,
        String reason
) {
    /** JSON(요청 본문 · AI 응답)은 항상 이 기본 생성자로 읽어요 — 생성자가 둘이라 Jackson 이 헷갈리지 않게 */
    @com.fasterxml.jackson.annotation.JsonCreator
    public ScheduleEditOp {
    }

    /** 이유 없이 (예전 클라이언트 · 테스트) */
    public ScheduleEditOp(ScheduleEditOpType type, Long targetId, List<Weekday> days, LocalTime startTime,
                          Integer durationMinutes, LocalDate date) {
        this(type, targetId, days, startTime, durationMinutes, date, null);
    }
}
