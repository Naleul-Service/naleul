package com.naleul.naleul.domain.goalCreation.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.slot.GoalSlots;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * JSON 컬럼(slots, quick_replies, payload) ↔ DTO 변환.
 * Spring이 만든 ObjectMapper를 써야 LocalDate/LocalTime 직렬화 설정(JavaTimeModule)이 그대로 적용된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoalJsonConverter {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("JSON 직렬화 실패: {}", value.getClass().getSimpleName(), e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public GoalSlots toSlots(String json) {
        if (json == null || json.isBlank()) return GoalSlots.empty();
        return read(json, GoalSlots.class);
    }

    public GoalPlanDto toPlan(String json) {
        if (json == null || json.isBlank()) return null;
        return read(json, GoalPlanDto.class);
    }

    public List<String> toStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException e) {
            log.error("JSON 역직렬화 실패: List<String>", e);
            return List.of();   // 빠른 답변 칩은 없어도 화면이 동작하므로 에러로 막지 않음
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.error("JSON 역직렬화 실패: {}", type.getSimpleName(), e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
