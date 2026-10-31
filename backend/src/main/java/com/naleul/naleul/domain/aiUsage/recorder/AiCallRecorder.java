package com.naleul.naleul.domain.aiUsage.recorder;

import com.naleul.naleul.domain.aiUsage.enums.AiFeature;

/**
 * Claude 호출 결과를 ai_call_log 에 남긴다 (AI 비용 대시보드용).
 *
 * 규칙: 기록이 실패해도 AI 기능은 그대로 동작해야 한다 → 구현체는 절대 예외를 던지지 않는다.
 */
public interface AiCallRecorder {

    /** 성공한 호출 — Anthropic 응답 JSON 원문에서 model · usage 만 꺼내 저장 */
    void recordSuccess(AiFeature feature, String responseJson, long latencyMs);

    /** 실패한 호출 (API 오류 · 타임아웃). 과금은 없지만 실패율을 보려고 남긴다 */
    void recordFailure(AiFeature feature, String model, long latencyMs);

    /** 테스트 · 수동 실행용: 아무것도 하지 않는다 */
    static AiCallRecorder noop() {
        return new AiCallRecorder() {
            @Override
            public void recordSuccess(AiFeature feature, String responseJson, long latencyMs) {
            }

            @Override
            public void recordFailure(AiFeature feature, String model, long latencyMs) {
            }
        };
    }
}
