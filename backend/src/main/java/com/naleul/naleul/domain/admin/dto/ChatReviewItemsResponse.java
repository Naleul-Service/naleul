package com.naleul.naleul.domain.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/v1/admin/dashboard/chat-review/items — 항목 상세 (표에서 행을 눌렀을 때).
 * 그 항목을 고치거나 직접 채운 세션을 기간 안에서 전부 (20개씩). 개인 식별 정보(이름 · 이메일 · id)는 넣지 않는다.
 */
public record ChatReviewItemsResponse(
        ChatReviewResponse.FieldRow stats,
        int page,
        int size,
        long total,
        List<Item> items
) {

    /**
     * @param result       EDITED 고침 · ADDED 직접 채움
     * @param question     이 항목을 물은 AI 질문 (안 물었으면 null — 질문 5개 소진 등)
     * @param answerSource CHIP · TYPED · SKIP (안 물었으면 null)
     * @param retained     false 면 6개월 보관 기간이 지나 답 원문 · 값이 지워짐
     */
    public record Item(LocalDateTime at, String result, String question, List<String> chips,
                       String answer, String answerSource, String aiValue, String finalValue, boolean retained) {
    }
}
