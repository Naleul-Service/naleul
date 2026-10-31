package com.naleul.naleul.domain.habit.controller;

import com.naleul.naleul.domain.habit.dto.request.HabitCreateRequest;
import com.naleul.naleul.domain.habit.dto.request.HabitOrderRequest;
import com.naleul.naleul.domain.habit.dto.request.HabitUpdateRequest;
import com.naleul.naleul.domain.habit.dto.response.HabitHeatmapResponse;
import com.naleul.naleul.domain.habit.dto.response.HabitHomeResponse;
import com.naleul.naleul.domain.habit.dto.response.HabitResponse;
import com.naleul.naleul.domain.habit.service.HabitService;
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

/**
 * 기본 생활 습관 (V52)
 * GET    /habits?date=yyyy-MM-dd           습관 목록 (그날 해야 하는지 · 했는지, 연속 기록, 30일 실천률). 그릇이 없으면 만든다
 * GET    /habits/heatmap?from=&to=          잔디맵 (기본 오늘까지 26주, 최대 400일)
 * POST   /habits                            추가
 * PATCH  /habits/{habitId}                  수정
 * PATCH  /habits/{habitId}/pause            쉬기
 * PATCH  /habits/{habitId}/resume           다시 시작
 * DELETE /habits/{habitId}                  지우기
 * PUT    /habits/order                      순서 바꾸기
 * PUT    /habits/{habitId}/checks/{date}    그날 했어요
 * DELETE /habits/{habitId}/checks/{date}    체크 풀기
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/habits")
public class HabitController {

    private final HabitService habitService;

    @GetMapping
    public ResponseEntity<ApiResponse<HabitHomeResponse>> home(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.HABIT_FOUND, habitService.home(userId, date)));
    }

    @GetMapping("/heatmap")
    public ResponseEntity<ApiResponse<HabitHeatmapResponse>> heatmap(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.HABIT_FOUND, habitService.heatmap(userId, from, to)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HabitResponse>> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody HabitCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.HABIT_CREATED, habitService.create(userId, request)));
    }

    @PatchMapping("/{habitId}")
    public ResponseEntity<ApiResponse<HabitResponse>> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long habitId,
            @Valid @RequestBody HabitUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.HABIT_UPDATED, habitService.update(userId, habitId, request)));
    }

    @PatchMapping("/{habitId}/pause")
    public ResponseEntity<ApiResponse<HabitResponse>> pause(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long habitId
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.HABIT_PAUSED, habitService.pause(userId, habitId)));
    }

    @PatchMapping("/{habitId}/resume")
    public ResponseEntity<ApiResponse<HabitResponse>> resume(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long habitId
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.HABIT_RESUMED, habitService.resume(userId, habitId)));
    }

    @DeleteMapping("/{habitId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long habitId
    ) {
        habitService.delete(userId, habitId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.HABIT_DELETED, null));
    }

    @PutMapping("/order")
    public ResponseEntity<ApiResponse<HabitHomeResponse>> reorder(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody HabitOrderRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.HABIT_UPDATED, habitService.reorder(userId, request)));
    }

    @PutMapping("/{habitId}/checks/{date}")
    public ResponseEntity<ApiResponse<HabitResponse>> check(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long habitId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.HABIT_CHECKED, habitService.check(userId, habitId, date)));
    }

    @DeleteMapping("/{habitId}/checks/{date}")
    public ResponseEntity<ApiResponse<HabitResponse>> uncheck(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long habitId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.HABIT_UNCHECKED, habitService.uncheck(userId, habitId, date)));
    }
}
