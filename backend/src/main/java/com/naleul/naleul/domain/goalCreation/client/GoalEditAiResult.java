package com.naleul.naleul.domain.goalCreation.client;

import com.naleul.naleul.domain.goalEdit.dto.GoalEditOp;

import java.util.List;

/**
 * @param summary 한 줄 요약 ("운동 루틴을 주 2회로 줄이고 종료일을 1월 22일로 늦출게요")
 * @param ops     변경 목록 (서버가 다시 검증한다)
 */
public record GoalEditAiResult(String summary, List<GoalEditOp> ops, AiCallMeta meta) {
    public GoalEditAiResult {
        ops = ops != null ? ops : List.of();
    }
}
