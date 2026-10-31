package com.naleul.naleul.domain.goalEdit.controller;

import com.naleul.naleul.domain.goalEdit.dto.GoalEditApplyRequest;
import com.naleul.naleul.domain.goalEdit.dto.GoalEditPreviewRequest;
import com.naleul.naleul.domain.goalEdit.dto.GoalEditPreviewResponse;
import com.naleul.naleul.domain.goalEdit.service.GoalEditService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 목표 상세 "말로 고치기".
 * POST /goal-categories/{id}/ai-edit/preview  { "instruction": "운동 주 2회로 줄여줘" } → 변경안 (아직 아무것도 안 바뀜)
 * POST /goal-categories/{id}/ai-edit/apply    { "ops": [ ...미리보기에서 고른 op... ] } → 적용, { "applied": 2 }
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/goal-categories/{goalCategoryId}/ai-edit")
public class GoalEditController {

    private final GoalEditService goalEditService;

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<GoalEditPreviewResponse>> preview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long goalCategoryId,
            @Valid @RequestBody GoalEditPreviewRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.OK,
                goalEditService.preview(userId, goalCategoryId, request.instruction())));
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> apply(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long goalCategoryId,
            @Valid @RequestBody GoalEditApplyRequest request
    ) {
        int applied = goalEditService.apply(userId, goalCategoryId, request.ops());
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_UPDATED, Map.of("applied", applied)));
    }
}
