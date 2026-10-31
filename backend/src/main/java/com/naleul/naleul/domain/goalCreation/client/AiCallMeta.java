package com.naleul.naleul.domain.goalCreation.client;

/** 호출 기록용 메타데이터. goal_draft의 ai_model/input_tokens/output_tokens에 저장 */
public record AiCallMeta(String model, Integer inputTokens, Integer outputTokens, long latencyMs) {
}
