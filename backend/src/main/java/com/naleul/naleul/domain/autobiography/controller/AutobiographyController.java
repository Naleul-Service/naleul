package com.naleul.naleul.domain.autobiography.controller;

import com.naleul.naleul.domain.autobiography.dto.request.*;
import com.naleul.naleul.domain.autobiography.dto.response.*;
import com.naleul.naleul.domain.autobiography.service.AutobiographyService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/autobiography")
public class AutobiographyController {

    private final AutobiographyService autobiographyService;

    // ── 질문 ──────────────────────────────────────────────

    // 커스텀 질문 생성
    @PostMapping("/questions")
    public ResponseEntity<ApiResponse<QuestionWithAnswerResponse>> createQuestion(
            @Valid @RequestBody QuestionCreateRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        QuestionWithAnswerResponse response = autobiographyService.createQuestion(request, userId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.AUTOBIOGRAPHY_QUESTION_CREATED, response));
    }

    // 질문 + 답변 전체 조회
    @GetMapping("/questions")
    public ResponseEntity<ApiResponse<List<QuestionWithAnswerResponse>>> getAllMyQuestions(
            @AuthenticationPrincipal Long userId
    ) {
        List<QuestionWithAnswerResponse> response = autobiographyService.getAllMyQuestions(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.AUTOBIOGRAPHY_QUESTION_FOUND, response));
    }

    // 질문 + 답변 단건 조회
    @GetMapping("/questions/{questionId}")
    public ResponseEntity<ApiResponse<QuestionWithAnswerResponse>> getQuestion(
            @PathVariable Long questionId,
            @AuthenticationPrincipal Long userId
    ) {
        QuestionWithAnswerResponse response = autobiographyService.getQuestion(questionId, userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.AUTOBIOGRAPHY_QUESTION_FOUND, response));
    }

    // 질문 수정 (CUSTOM 질문만 가능)
    @PatchMapping("/questions/{questionId}")
    public ResponseEntity<ApiResponse<QuestionWithAnswerResponse>> updateQuestion(
            @PathVariable Long questionId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody QuestionUpdateRequest request
    ) {
        QuestionWithAnswerResponse response =
                autobiographyService.updateQuestion(questionId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.AUTOBIOGRAPHY_QUESTION_UPDATED, response));
    }

    // 질문 삭제 (CUSTOM 질문만 가능)
    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(
            @PathVariable Long questionId,
            @AuthenticationPrincipal Long userId
    ) {
        autobiographyService.deleteQuestion(questionId, userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.AUTOBIOGRAPHY_QUESTION_DELETED, null));
    }

    // ── 답변 ──────────────────────────────────────────────

    // 답변 생성
    @PostMapping("/questions/{questionId}/answers")
    public ResponseEntity<ApiResponse<QuestionWithAnswerResponse>> createAnswer(
            @PathVariable Long questionId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AnswerCreateRequest request
    ) {
        QuestionWithAnswerResponse response =
                autobiographyService.createAnswer(questionId, userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.AUTOBIOGRAPHY_ANSWER_CREATED, response));
    }

    // 답변 수정
    @PatchMapping("/answers/{answerId}")
    public ResponseEntity<ApiResponse<AnswerResponse>> updateAnswer(
            @PathVariable Long answerId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AnswerUpdateRequest request
    ) {
        AnswerResponse response = autobiographyService.updateAnswer(answerId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.AUTOBIOGRAPHY_ANSWER_UPDATED, response));
    }

    // 답변 삭제
    @DeleteMapping("/answers/{answerId}")
    public ResponseEntity<ApiResponse<Void>> deleteAnswer(
            @PathVariable Long answerId,
            @AuthenticationPrincipal Long userId
    ) {
        autobiographyService.deleteAnswer(answerId, userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.AUTOBIOGRAPHY_ANSWER_DELETED, null));
    }
}