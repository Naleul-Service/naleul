package com.naleul.naleul.domain.goalOutcome.controller;

import com.naleul.naleul.domain.goalOutcome.dto.OutcomePromptResponse;
import com.naleul.naleul.domain.goalOutcome.service.GoalOutcomeRecorder;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 결과 확인 모달 — 기한이 지나 자동 판정된 목표를 사용자에게 다시 묻는다.
 *
 * 왜 필요한가: 기간이 끝나면 서버가 수치(80%) · 실천률로 자동 판정하는데, 사용자가 직접 고른 결과와 얼마나 맞는지
 * (판정 일치율) 알아야 "목표 달성률" 숫자를 믿을 수 있다. 목표 상세에 들어가야만 고를 수 있으면 답이 거의 안 모인다.
 *
 * GET  /api/v1/goal-outcomes/prompts                       앱을 열 때 — 물어볼 목표 목록 (오래된 순, 최근 30일)
 * POST /api/v1/goal-outcomes/prompts/{goalCategoryId}/dismiss  "나중에" — 자동 판정 그대로, 다시 묻지 않음
 * 답하기는 기존 PATCH /api/v1/goal-categories/{id}/outcome (결과를 직접 고르면 ANSWERED 로 바뀐다)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/goal-outcomes/prompts")
public class GoalOutcomePromptController {

    private final GoalOutcomeRecorder outcomeRecorder;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OutcomePromptResponse>>> list(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_OUTCOME_PROMPTS_FOUND,
                outcomeRecorder.pendingPrompts(userId)));
    }

    @PostMapping("/{goalCategoryId}/dismiss")
    public ResponseEntity<ApiResponse<Void>> dismiss(@AuthenticationPrincipal Long userId,
                                                     @PathVariable Long goalCategoryId) {
        outcomeRecorder.dismissPrompt(userId, goalCategoryId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_OUTCOME_PROMPT_DISMISSED));
    }
}
