package com.naleul.naleul.domain.lifePattern.controller;

import com.naleul.naleul.domain.lifePattern.dto.request.LifePatternCreateRequest;
import com.naleul.naleul.domain.lifePattern.dto.request.LifePatternOverrideRequest;
import com.naleul.naleul.domain.lifePattern.dto.request.LifePatternScopedChangeRequest;
import com.naleul.naleul.domain.lifePattern.dto.request.LifePatternUpdateRequest;
import com.naleul.naleul.domain.lifePattern.dto.response.DayFixedBlocksResponse;
import com.naleul.naleul.domain.lifePattern.dto.response.LifePatternResponse;
import com.naleul.naleul.domain.lifePattern.dto.response.LifePatternScopedChangeResponse;
import com.naleul.naleul.domain.lifePattern.service.LifePatternService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 기본 생활 패턴 (명세 3 · 5-1)
 *
 * GET    /api/v1/life-patterns                                   목록 (처음이면 기본 3개 생성)
 * POST   /api/v1/life-patterns                                   추가
 * PUT    /api/v1/life-patterns/{id}                              수정 (이후 모든 날짜 기본값)
 * DELETE /api/v1/life-patterns/{id}                              삭제
 * PUT    /api/v1/life-patterns/{id}/overrides/{date}             그날만 변경 / 비우기
 * PUT    /api/v1/life-patterns/{id}/scoped-change                 범위 골라 변경 (이날만 / 이번 주 / 앞으로)
 * DELETE /api/v1/life-patterns/{id}/overrides/{date}             그날 변경 취소
 * GET    /api/v1/life-patterns/fixed-blocks?startDate=&endDate=  기간의 고정 블록 + 하루 범위 (최대 7일)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/life-patterns")
public class LifePatternController {

    private final LifePatternService lifePatternService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LifePatternResponse>>> getPatterns(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.LIFE_PATTERNS_FOUND,
                lifePatternService.getPatterns(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LifePatternResponse>> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody LifePatternCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.LIFE_PATTERN_CREATED, lifePatternService.create(userId, request)));
    }

    @PutMapping("/{lifePatternId}")
    public ResponseEntity<ApiResponse<LifePatternResponse>> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long lifePatternId,
            @Valid @RequestBody LifePatternUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.LIFE_PATTERN_UPDATED,
                lifePatternService.update(userId, lifePatternId, request)));
    }

    @DeleteMapping("/{lifePatternId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long lifePatternId) {
        lifePatternService.delete(userId, lifePatternId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.LIFE_PATTERN_DELETED));
    }

    @PutMapping("/{lifePatternId}/scoped-change")
    public ResponseEntity<ApiResponse<LifePatternScopedChangeResponse>> changeWithScope(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long lifePatternId,
            @Valid @RequestBody LifePatternScopedChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.LIFE_PATTERN_SCOPED_CHANGED,
                lifePatternService.changeWithScope(userId, lifePatternId, request)));
    }

    @PutMapping("/{lifePatternId}/overrides/{targetDate}")
    public ResponseEntity<ApiResponse<DayFixedBlocksResponse>> putOverride(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long lifePatternId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate,
            @RequestBody LifePatternOverrideRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.LIFE_PATTERN_OVERRIDE_SAVED,
                lifePatternService.putOverride(userId, lifePatternId, targetDate, request)));
    }

    @DeleteMapping("/{lifePatternId}/overrides/{targetDate}")
    public ResponseEntity<ApiResponse<DayFixedBlocksResponse>> deleteOverride(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long lifePatternId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.LIFE_PATTERN_OVERRIDE_DELETED,
                lifePatternService.deleteOverride(userId, lifePatternId, targetDate)));
    }

    @GetMapping("/fixed-blocks")
    public ResponseEntity<ApiResponse<List<DayFixedBlocksResponse>>> getFixedBlocks(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.FIXED_BLOCKS_FOUND,
                lifePatternService.getFixedBlocks(userId, startDate, endDate)));
    }
}
