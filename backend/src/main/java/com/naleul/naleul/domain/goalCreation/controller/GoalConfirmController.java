package com.naleul.naleul.domain.goalCreation.controller;

import com.naleul.naleul.domain.goalCreation.dto.request.GoalConfirmRequest;
import com.naleul.naleul.domain.goalCreation.dto.response.GoalConfirmResponse;
import com.naleul.naleul.domain.goalCreation.service.GoalConfirmService;
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
@RequestMapping("/api/v1/goal-creation/sessions/{sessionId}/confirm")
public class GoalConfirmController {

    private final GoalConfirmService confirmService;

    // "이 목표로 시작하기" → 201 { goalId }, 검증 실패 시 422 + data.violations
    @PostMapping
    public ResponseEntity<ApiResponse<GoalConfirmResponse>> confirm(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GoalConfirmRequest request
    ) {
        GoalConfirmResponse response = confirmService.confirm(userId, sessionId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.GOAL_DRAFT_CONFIRMED, response));
    }
}
