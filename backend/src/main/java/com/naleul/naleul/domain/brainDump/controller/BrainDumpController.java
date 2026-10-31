package com.naleul.naleul.domain.brainDump.controller;

import com.naleul.naleul.domain.brainDump.dto.request.BrainDumpConfirmRequest;
import com.naleul.naleul.domain.brainDump.dto.request.BrainDumpParseRequest;
import com.naleul.naleul.domain.brainDump.dto.response.BrainDumpConfirmResponse;
import com.naleul.naleul.domain.brainDump.dto.response.BrainDumpParseResponse;
import com.naleul.naleul.domain.brainDump.service.BrainDumpService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * POST /api/v1/brain-dump/parse    1단계 → 2단계: 해석 + 목표 연결 제안 (저장하지 않음)
 * POST /api/v1/brain-dump/confirm  2단계 → 3단계: 저장 + 즉시 배치
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/brain-dump")
public class BrainDumpController {

    private final BrainDumpService brainDumpService;

    @PostMapping("/parse")
    public ResponseEntity<ApiResponse<BrainDumpParseResponse>> parse(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody BrainDumpParseRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.BRAIN_DUMP_PARSED, brainDumpService.parse(userId, request)));
    }

    /** 겹치는 일정이 있으면 200 + created=false + conflicts (아무것도 저장 안 됨) */
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<BrainDumpConfirmResponse>> confirm(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody BrainDumpConfirmRequest request) {
        BrainDumpConfirmResponse response = brainDumpService.confirm(userId, request);
        return ResponseEntity.status(response.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success(response.created() ? SuccessCode.BRAIN_DUMP_CONFIRMED : SuccessCode.BRAIN_DUMP_CONFLICTED,
                        response));
    }
}
