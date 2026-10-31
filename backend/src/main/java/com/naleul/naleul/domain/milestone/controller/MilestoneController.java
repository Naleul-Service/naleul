package com.naleul.naleul.domain.milestone.controller;

import com.naleul.naleul.domain.goalCategory.dto.response.GoalCategoryResponse.MilestoneInfo;
import com.naleul.naleul.domain.milestone.dto.request.MilestoneCreateRequest;
import com.naleul.naleul.domain.milestone.dto.request.MilestoneUpdateRequest;
import com.naleul.naleul.domain.milestone.service.MilestoneService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 마일스톤 (목표 상세)
 *
 * POST   /api/v1/goal-categories/{goalCategoryId}/milestones   추가
 * PATCH  /api/v1/milestones/{milestoneId}                       수정 · 달성 체크/취소
 * DELETE /api/v1/milestones/{milestoneId}                       삭제 (연결된 Task 는 남고 연결만 끊김)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class MilestoneController {

    private final MilestoneService milestoneService;

    @PostMapping("/goal-categories/{goalCategoryId}/milestones")
    public ResponseEntity<ApiResponse<MilestoneInfo>> create(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long goalCategoryId,
            @Valid @RequestBody MilestoneCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.MILESTONE_CREATED,
                        milestoneService.create(userId, goalCategoryId, request)));
    }

    @PatchMapping("/milestones/{milestoneId}")
    public ResponseEntity<ApiResponse<MilestoneInfo>> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long milestoneId,
            @Valid @RequestBody MilestoneUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MILESTONE_UPDATED,
                milestoneService.update(userId, milestoneId, request)));
    }

    @DeleteMapping("/milestones/{milestoneId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long milestoneId) {
        milestoneService.delete(userId, milestoneId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MILESTONE_DELETED));
    }
}
