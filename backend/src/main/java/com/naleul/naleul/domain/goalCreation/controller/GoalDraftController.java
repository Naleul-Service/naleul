package com.naleul.naleul.domain.goalCreation.controller;

import com.naleul.naleul.domain.goalCreation.dto.request.GoalDraftRequest;
import com.naleul.naleul.domain.goalCreation.dto.response.GoalDraftRequestedResponse;
import com.naleul.naleul.domain.goalCreation.dto.response.GoalDraftResponse;
import com.naleul.naleul.domain.goalCreation.service.GoalDraftService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/goal-creation/sessions/{sessionId}/drafts")
public class GoalDraftController {

    private final GoalDraftService draftService;

    // 초안 생성 / 다시 생성 요청 → 202, 결과는 폴링으로
    @PostMapping
    public ResponseEntity<ApiResponse<GoalDraftRequestedResponse>> requestDraft(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody(required = false) GoalDraftRequest request
    ) {
        GoalDraftRequest body = request != null ? request : new GoalDraftRequest();
        GoalDraftRequestedResponse response = draftService.requestDraft(userId, sessionId, body);
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(SuccessCode.GOAL_DRAFT_REQUESTED, response));
    }

    // 초안 폴링 (2초 간격, 최대 90초)
    @GetMapping("/{draftId}")
    public ResponseEntity<ApiResponse<GoalDraftResponse>> getDraft(
            @PathVariable Long sessionId,
            @PathVariable Long draftId,
            @AuthenticationPrincipal Long userId
    ) {
        GoalDraftResponse response = draftService.getDraft(userId, sessionId, draftId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_DRAFT_FOUND, response));
    }
}
