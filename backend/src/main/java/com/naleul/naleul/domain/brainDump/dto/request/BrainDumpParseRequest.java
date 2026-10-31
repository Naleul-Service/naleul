package com.naleul.naleul.domain.brainDump.dto.request;

import com.naleul.naleul.domain.brainDump.enums.DateType;
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
 * 1단계 → 2단계: Brain dump 해석 요청.
 * 칩으로 고른 날짜/시간(date, dateType, startTime, endTime)이 있으면 AI 해석보다 우선한다.
 */
public record BrainDumpParseRequest(
        @NotEmpty(message = "할 일을 한 개 이상 입력해주세요.")
        @Size(max = 20, message = "한 번에 최대 20개까지 입력할 수 있어요.")
        List<@Valid Item> items
) {
    public record Item(
            @NotBlank @Size(max = 40) String clientKey,
            @NotBlank(message = "할 일 내용을 입력해주세요.") @Size(max = 200, message = "200자 이하로 입력해주세요.") String text,
            LocalDate date,
            DateType dateType,
            LocalTime startTime,
            LocalTime endTime,
            /** 사용자가 정한 소요 시간(분). 시작 시각과 상관없이 "3시간 걸리는 일" — AI 추정보다 우선 */
            @Min(value = 5, message = "소요 시간은 5분 이상이어야 해요.")
            @Max(value = 480, message = "소요 시간은 8시간까지 정할 수 있어요.")
            Integer estimatedMinutes
    ) {
        /** 소요 시간 없이 만들 때 (기존 호출부 호환) */
        public Item(String clientKey, String text, LocalDate date, DateType dateType, LocalTime startTime, LocalTime endTime) {
            this(clientKey, text, date, dateType, startTime, endTime, null);
        }
    }
}
