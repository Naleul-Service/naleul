package com.naleul.naleul.domain.planner.dto.response;

/**
 * Free 하루 Task 추가 한도. Brain dump 입력창이 remaining 에 닿으면 멈춘다.
 * Pro 는 dailyLimit/remaining 이 null (무제한).
 */
public record TaskQuotaResponse(boolean premium, Integer dailyLimit, long usedToday, Integer remaining) {
}
