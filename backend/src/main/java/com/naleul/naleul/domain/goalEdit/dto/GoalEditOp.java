package com.naleul.naleul.domain.goalEdit.dto;

import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 변경 하나. AI 가 만들고(미리보기), 사용자가 확인한 것만 다시 보내 적용한다.
 * 종류(type)마다 쓰는 필드가 다르고, 안 쓰는 필드는 null.
 *
 * @param targetId        바꿀/지울 루틴·점검 시점 id (UPDATE_* / DELETE_*)
 * @param title           이름 (목표·루틴·점검 시점·할 일)
 * @param durationMinutes 루틴·할 일 길이(분)
 * @param days            루틴 요일 ["MON", "WED"]
 * @param startTime       루틴·할 일 시작 시각 (없으면 시간 미정)
 * @param description     루틴 하는 방법
 * @param date            UPDATE_GOAL: 새 종료일 / 점검 시점: 날짜 / ADD_TASK: 하는 날
 * @param startValue      UPDATE_GOAL: 새 시작값
 * @param targetValue     UPDATE_GOAL: 새 목표값
 * @param areaId          ADD_ROUTINE · ADD_TASK: 넣을 영역(세부 목표) id, 없으면 "기타 할 일"
 */
public record GoalEditOp(
        @NotNull GoalEditOpType type,
        Long targetId,
        String title,
        Integer durationMinutes,
        List<Weekday> days,
        LocalTime startTime,
        String description,
        LocalDate date,
        BigDecimal startValue,
        BigDecimal targetValue,
        Long areaId
) {
}
