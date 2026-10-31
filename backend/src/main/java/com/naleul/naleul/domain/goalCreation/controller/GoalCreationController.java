package com.naleul.naleul.domain.goalCreation.controller;

import com.naleul.naleul.domain.goalCreation.dto.request.GoalAnswerRequest;
import com.naleul.naleul.domain.goalCreation.dto.request.GoalSessionStartRequest;
import com.naleul.naleul.domain.goalCreation.dto.request.GoalSlotsUpdateRequest;
import com.naleul.naleul.domain.goalCreation.dto.response.GoalSessionDetailResponse;
import com.naleul.naleul.domain.goalCreation.dto.response.GoalSessionTurnResponse;
import com.naleul.naleul.domain.goalCreation.reader.GoalCreationSessionReader;
import com.naleul.naleul.domain.goalCreation.service.GoalInterviewService;
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
@RequestMapping("/api/v1/goal-creation/sessions")
public class GoalCreationController {

    private final GoalInterviewService interviewService;
    private final GoalCreationSessionReader sessionReader;

    // 세션 시작 + 첫 질문
    @PostMapping
    public ResponseEntity<ApiResponse<GoalSessionTurnResponse>> start(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody(required = false) GoalSessionStartRequest request
    ) {
        GoalSessionStartRequest body = request != null ? request : new GoalSessionStartRequest();
        GoalSessionTurnResponse response = interviewService.start(userId, body);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.GOAL_SESSION_STARTED, response));
    }

    // 진행 중인 세션 (앱 재실행 시 "이어서 만들기"). 없으면 204
    // ⚠️ "/{sessionId}"보다 위에 둘 필요는 없지만(스프링이 고정 경로를 우선함) 읽기 쉽게 먼저 둠
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<GoalSessionDetailResponse>> getActive(
            @AuthenticationPrincipal Long userId
    ) {
        return sessionReader.getActiveSession(userId)
                .map(r -> ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_SESSION_FOUND, r)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // 세션 조회 (대화 기록 + 슬롯 + 최신 초안)
    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<GoalSessionDetailResponse>> getSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Long userId
    ) {
        GoalSessionDetailResponse response = sessionReader.getSession(sessionId, userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_SESSION_FOUND, response));
    }

    // 답변 / 건너뛰기
    @PostMapping("/{sessionId}/answers")
    public ResponseEntity<ApiResponse<GoalSessionTurnResponse>> answer(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GoalAnswerRequest request
    ) {
        GoalSessionTurnResponse response = interviewService.answer(userId, sessionId, request);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_SESSION_ANSWERED, response));
    }

    // "바로 계획 만들기"
    @PostMapping("/{sessionId}/finish-interview")
    public ResponseEntity<ApiResponse<GoalSessionTurnResponse>> finishInterview(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Long userId
    ) {
        GoalSessionTurnResponse response = interviewService.finishInterview(userId, sessionId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_SESSION_ANSWERED, response));
    }

    // 정리 확인 화면에서 슬롯 수정
    @PatchMapping("/{sessionId}/slots")
    public ResponseEntity<ApiResponse<GoalSessionTurnResponse>> updateSlots(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GoalSlotsUpdateRequest request
    ) {
        GoalSessionTurnResponse response = interviewService.updateSlots(userId, sessionId, request);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_SESSION_SLOTS_UPDATED, response));
    }

    // 취소
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Long userId
    ) {
        interviewService.cancel(userId, sessionId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_SESSION_CANCELLED));
    }
}
