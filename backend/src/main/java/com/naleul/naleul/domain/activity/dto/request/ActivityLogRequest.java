package com.naleul.naleul.domain.activity.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * "한 일" 빠른 기록 (Task 추가의 Brain dump 처럼 한 줄씩).
 * 예) { "items": [ { "clientKey": "c1", "text": "저녁 8시 나를 회의 1시간", "goalCategoryId": 12 } ] }
 *
 * - text 만 보내면 서버가 문장에서 제목·날짜·시각·걸린 시간을 읽는다 ("어제", "오후 3시", "30분" …).
 * - AI 해석 결과(POST /brain-dump/parse)를 그대로 쓰려면 title·date·startTime·endTime·minutes 를 같이 보낸다 (보낸 값이 우선).
 * - 목표는 goalCategoryId(기존 목표) 또는 tempGoalKey(이번에 새로 만들 임시 목표) 중 하나, 둘 다 없으면 목표 없이 기록.
 * - 시각이 없으면 "방금 끝난 일"로 보고, 다른 기록과 겹치지 않게 지금부터 거꾸로 채운다.
 */
public record ActivityLogRequest(
        @NotEmpty(message = "기록할 내용을 입력해 주세요.")
        @Size(max = 20, message = "한 번에 20개까지 기록할 수 있어요.")
        List<@Valid Item> items,

        List<@Valid TempGoal> tempGoals
) {
    public ActivityLogRequest {
        tempGoals = tempGoals != null ? tempGoals : List.of();
    }

    public record Item(
            @NotBlank String clientKey,
            @NotBlank(message = "무엇을 했는지 입력해 주세요.")
            @Size(max = 200) String text,
            @Size(max = 60) String title,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            @Min(1) @Max(1440) Integer minutes,
            Long goalCategoryId,
            String tempGoalKey
    ) {
    }

    public record TempGoal(
            @NotBlank String tempKey,
            @NotBlank @Size(max = 40) String name,
            @Size(max = 16) String emoji,
            Long colorId
    ) {
    }
}
