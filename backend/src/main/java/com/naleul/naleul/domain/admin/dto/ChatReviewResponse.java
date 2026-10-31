package com.naleul.naleul.domain.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/v1/admin/dashboard/chat-review — AI 채팅 정리 정확도 (운영진 전용, V67).
 * 기준: 대화가 끝난 순간의 AI 정리값 vs 처음 "계획 만들기"를 누른 순간의 값. 정리 화면에서 바뀐 것만.
 * 비율(%)은 분모가 0이면 null. 표본이 minSample 미만인 항목은 화면에서 흐리게 보여준다.
 */
public record ChatReviewResponse(
        Period period,
        Filters filters,
        long sessions,
        Overall overall,
        List<FieldRow> fields,
        Exit exit,
        List<String> promptVersions,
        int minSample
) {

    public record Period(LocalDate from, LocalDate to, int days, LocalDateTime generatedAt) {
    }

    public record Filters(String goalType, String promptVersion, String answerSource) {
    }

    /**
     * @param progressRate   대표: 아무것도 안 고치고 · 직접 채우지도 않고 계획 만들기 ÷ 계획 만들기 누른 세션
     * @param editRate       7개 중 1개 이상 고친 세션 ÷ 계획 만들기 누른 세션
     * @param missRate       5개 중 1개 이상 직접 채운 세션 ÷ 계획 만들기 누른 세션
     * @param avgEditedItems 고친 항목 수 합계 ÷ 1개 이상 고친 세션
     */
    public record Overall(Integer progressRate, Integer editRate, Integer missRate,
                          long untouchedSessions, long editedSessions, long addedSessions, BigDecimal avgEditedItems) {
    }

    /**
     * @param aiFilled     AI 가 채운 세션 (수정률 · 진행률의 분모)
     * @param editRate     고친 세션 ÷ AI 가 채운 세션
     * @param chipRate     이 항목을 물은 질문에 칩을 눌러 답한 비율 (칩 + 직접 답 중)
     * @param typedRate    직접 써서 답한 비율
     * @param emptyBase    AI 가 비워 둔 세션 (누락률의 분모)
     * @param missRate     직접 채운 세션 ÷ AI 가 비워 둔 세션 (이유 · 실행 성향은 null)
     * @param progressRate AI 값을 그대로 둔 세션 ÷ AI 가 채운 세션 (= 100 − 수정률)
     */
    public record FieldRow(String key, String label, long aiFilled, long edited, Integer editRate,
                           long chipAnswers, long typedAnswers, Integer chipRate, Integer typedRate,
                           long emptyBase, long added, Integer missRate, Integer progressRate) {
    }

    /**
     * @param exitRate 계획 만들기 없이 끝난 세션(취소 · 7일 만료) ÷ (정리 화면 진입 − 아직 정리 화면에 머무는 세션)
     */
    public record Exit(long entered, long reviewed, long exited, long pending, Integer exitRate) {
    }
}
