package com.naleul.naleul.domain.work.dto.request;

import com.naleul.naleul.domain.work.enums.WorkBoardColumn;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 업무 Task 고치기 (V52) — 보낸 값만 바꾼다. 지우려면 clear 플래그.
 * - date: 그날로 옮김 (시간이 정해져 있었으면 시간은 풀린다) · clearDate: "할 일 목록"으로
 * - status: 보드 칸 옮기기 (NOT_STARTED · IN_PROGRESS · DONE)
 * - memo: "" 이면 지운다
 */
public record WorkTaskUpdateRequest(
        @Size(max = 100, message = "Task 이름은 100자 이하로 입력해주세요.")
        String taskName,

        Long topicId,
        Boolean clearTopic,

        LocalDate date,
        Boolean clearDate,

        LocalDate dueDate,
        Boolean clearDueDate,

        @Size(max = 500, message = "메모는 500자 이하로 입력해 주세요.")
        String memo,

        WorkBoardColumn status
) {
}
