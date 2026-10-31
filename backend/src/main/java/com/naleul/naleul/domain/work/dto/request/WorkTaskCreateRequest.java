package com.naleul.naleul.domain.work.dto.request;

import com.naleul.naleul.domain.work.enums.WorkBoardColumn;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 업무 Task 하나 추가 (V52)
 * 예) { "taskName": "API 명세 정리", "topicId": 3, "date": "2026-10-14", "dueDate": "2026-10-17" }
 * - date 를 비우면 캘린더에 안 나오는 "할 일 목록"에 쌓인다. 정하면 그날 "시간 미정" Task (자동 배치 대상)
 * - status 를 비우면 시작 전
 */
public record WorkTaskCreateRequest(
        @NotBlank(message = "Task 이름은 필수입니다.")
        @Size(max = 100, message = "Task 이름은 100자 이하로 입력해주세요.")
        String taskName,

        Long topicId,

        LocalDate date,

        LocalDate dueDate,

        @Size(max = 500, message = "메모는 500자 이하로 입력해 주세요.")
        String memo,

        WorkBoardColumn status
) {
}
