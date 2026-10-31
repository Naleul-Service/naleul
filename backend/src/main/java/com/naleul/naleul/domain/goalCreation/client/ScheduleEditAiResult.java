package com.naleul.naleul.domain.goalCreation.client;

import com.naleul.naleul.domain.scheduleEdit.dto.ScheduleEditOp;

import java.util.List;

/**
 * @param summary 한 줄 요약 ("겹치던 기출 풀기를 화·목 19시로 옮길게요")
 * @param ops     변경 목록 (서버가 다시 검증한다)
 */
public record ScheduleEditAiResult(String summary, List<ScheduleEditOp> ops, AiCallMeta meta) {
    public ScheduleEditAiResult {
        ops = ops != null ? ops : List.of();
    }
}
