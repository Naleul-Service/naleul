package com.naleul.naleul.domain.scheduleEdit.controller;

import com.naleul.naleul.domain.scheduleEdit.dto.ScheduleEditApplyRequest;
import com.naleul.naleul.domain.scheduleEdit.dto.ScheduleEditApplyResponse;
import com.naleul.naleul.domain.scheduleEdit.dto.ScheduleEditPreviewRequest;
import com.naleul.naleul.domain.scheduleEdit.dto.ScheduleEditPreviewResponse;
import com.naleul.naleul.domain.scheduleEdit.service.ScheduleEditService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 캘린더 "AI로 일정 조정".
 * POST /timetable/ai-edit/preview { "instruction": "겹치는 루틴 정리해줘", "startDate": "2026-10-12", "endDate": "2026-10-18" }
 *      → 변경안 (아직 아무것도 안 바뀜) + 지금 겹쳐 있는 루틴
 * POST /timetable/ai-edit/apply   { "ops": [ ...미리보기에서 고른 op... ], "startDate": ..., "endDate": ... }
 *      → { "applied": 2, "skipped": ["Task '…' — 그 시간에 직접 정한 다른 일정이 있어요"] }
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/timetable/ai-edit")
public class ScheduleEditController {

    private final ScheduleEditService scheduleEditService;

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<ScheduleEditPreviewResponse>> preview(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ScheduleEditPreviewRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.OK,
                scheduleEditService.preview(userId, request.instruction(), request.startDate(), request.endDate())));
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<ScheduleEditApplyResponse>> apply(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ScheduleEditApplyRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.OK,
                scheduleEditService.apply(userId, request.ops(), request.startDate(), request.endDate())));
    }
}
