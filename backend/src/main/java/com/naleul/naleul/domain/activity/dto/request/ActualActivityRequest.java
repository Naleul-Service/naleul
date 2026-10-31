package com.naleul.naleul.domain.activity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 실제로 한 일 기록 (추가·수정 공통, 폼 전체를 보냄).
 * 예) { "title": "친구 만남", "startAt": "2026-10-06T19:00:00", "endAt": "2026-10-06T20:30:00",
 *       "replacedTaskId": 91 }
 * - 시각은 한국 시간. 끝이 시작보다 늦어야 하고 최대 24시간, 아직 오지 않은 시간은 기록할 수 없다.
 * - goalCategoryId · replacedTaskId · emoji · memo 는 선택 (null 이면 비움)
 */
public record ActualActivityRequest(
        @NotBlank(message = "무엇을 했는지 입력해주세요.")
        @Size(max = 60, message = "60자 이하로 입력해주세요.")
        String title,

        @Size(max = 16)
        String emoji,

        @NotNull(message = "시작 시각은 필수입니다.")
        LocalDateTime startAt,

        @NotNull(message = "종료 시각은 필수입니다.")
        LocalDateTime endAt,

        Long goalCategoryId,

        Long replacedTaskId,

        @Size(max = 200, message = "메모는 200자 이하로 입력해주세요.")
        String memo
) {
}
