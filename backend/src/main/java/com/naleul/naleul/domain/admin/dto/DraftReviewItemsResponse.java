package com.naleul.naleul.domain.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/v1/admin/dashboard/draft-review/items?kind=ROUTINE — 항목 상세 (운영진 전용, V68).
 * 칸별 수정률 + 이 항목을 고치거나 지우거나 더한 사례 전부 (확정 전 · 확정 뒤).
 */
public record DraftReviewItemsResponse(
        String kind,
        String label,
        List<FieldStat> fields,
        int page,
        int size,
        long total,
        List<Case> cases
) {

    /**
     * @param base       AI 가 만든 이 칸 수
     * @param editRate   확정 전 고친 비율
     * @param afterRate  확정 뒤(첫 행동 후 window 일) 고친 비율
     */
    public record FieldStat(String field, String label, long base, long edited, Integer editRate,
                            long afterBase, long afterEdited, Integer afterRate) {
    }

    /**
     * @param stage    BEFORE_CONFIRM 확정 전 · AFTER_CONFIRM 확정 뒤
     * @param result   EDITED · DELETED · ADDED
     * @param request  그 세션의 말로 고치기 · 다시 생성 요청 문장 (순서대로)
     */
    public record Case(LocalDateTime at, String stage, String result, String itemLabel, String field, String fieldLabel,
                       String path, String request, String aiValue, String finalValue, String direction, Integer delta) {
    }
}
