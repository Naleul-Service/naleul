package com.naleul.naleul.domain.goalCreation.dto.slot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 슬롯 하나. JSON 예: { "status": "FILLED", "value": "몸무게 10kg 감량" }
 * isEmpty() · isFilled() 는 JSON 필드가 아니다 (Jackson 2.18 부터 record 의 isXxx() 도 필드로 써서
 * "empty" · "filled" 가 저장됐고, 알 수 없는 필드를 막는 ObjectMapper 에서는 다시 읽지 못했다).
 * 이미 저장된 세션 JSON 에 남아 있는 "empty" · "filled" 는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Slot<T>(SlotStatus status, T value) {

    public static <T> Slot<T> empty() {
        return new Slot<>(SlotStatus.EMPTY, null);
    }

    public static <T> Slot<T> filled(T value) {
        return new Slot<>(SlotStatus.FILLED, value);
    }

    public static <T> Slot<T> skipped() {
        return new Slot<>(SlotStatus.SKIPPED, null);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return status == null || status == SlotStatus.EMPTY;
    }

    @JsonIgnore
    public boolean isFilled() {
        return status == SlotStatus.FILLED;
    }
}
