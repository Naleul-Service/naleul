package com.naleul.naleul.domain.planner.controller;

import com.naleul.naleul.domain.planner.dto.request.PlannerSettingUpdateRequest;
import com.naleul.naleul.domain.planner.dto.response.PlannerSettingResponse;
import com.naleul.naleul.domain.planner.service.PlannerSettingService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/planner-settings")
public class PlannerSettingController {

    private final PlannerSettingService plannerSettingService;

    @GetMapping
    public ResponseEntity<ApiResponse<PlannerSettingResponse>> get(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.PLANNER_SETTING_FOUND,
                plannerSettingService.get(userId)));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<PlannerSettingResponse>> update(
            @AuthenticationPrincipal Long userId,
            @RequestBody PlannerSettingUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.PLANNER_SETTING_UPDATED,
                plannerSettingService.update(userId, request)));
    }
}
