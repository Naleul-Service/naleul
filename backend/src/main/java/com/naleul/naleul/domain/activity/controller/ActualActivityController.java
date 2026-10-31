package com.naleul.naleul.domain.activity.controller;

import com.naleul.naleul.domain.activity.dto.request.ActivityLogRequest;
import com.naleul.naleul.domain.activity.dto.request.ActualActivityRequest;
import com.naleul.naleul.domain.activity.dto.response.ActivityLogResponse;
import com.naleul.naleul.domain.activity.dto.response.ActualActivityResponse;
import com.naleul.naleul.domain.activity.dto.response.RecordJournalResponse;
import com.naleul.naleul.domain.activity.dto.response.RecordPatternResponse;
import com.naleul.naleul.domain.activity.service.ActivityLogService;
import com.naleul.naleul.domain.activity.service.ActualActivityService;
import com.naleul.naleul.domain.activity.service.RecordJournalService;
import com.naleul.naleul.domain.activity.service.RecordPatternService;
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
import java.time.LocalDateTime;
import java.util.List;

/**
 * 실제로 한 일.
 * GET    /activities?startDate=2026-10-05&endDate=2026-10-11   기간과 겹치는 기록 (최대 42일)
 * POST   /activities                                           기록 추가
 * PUT    /activities/{id}                                      수정 (폼 전체)
 * DELETE /activities/{id}                                      삭제
 * POST   /activities/logs                                      한 줄씩 빠른 기록 (문장에서 시각·걸린 시간을 읽음)
 * GET    /activities/goals/{goalId}?before=&size=              목표에 쌓인 기록, 최근 것부터 (기록형 목표 타임라인)
 * GET    /activities/goals/{goalId}/pattern                    이 목표에 대한 나의 패턴 (기록형 목표 상세)
 * GET    /activities/goals/{goalId}/journal?before=&days=      업무 일지 — 날짜별 완료한 Task + 직접 남긴 기록
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/activities")
public class ActualActivityController {

    private final ActualActivityService service;
    private final ActivityLogService logService;
    private final RecordPatternService patternService;
    private final RecordJournalService journalService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ActualActivityResponse>>> getRange(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.ACTIVITIES_FOUND,
                service.getRange(userId, startDate, endDate)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ActualActivityResponse>> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ActualActivityRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.ACTIVITY_CREATED, service.create(userId, request)));
    }

    @PutMapping("/{activityId}")
    public ResponseEntity<ApiResponse<ActualActivityResponse>> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long activityId,
            @Valid @RequestBody ActualActivityRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.ACTIVITY_UPDATED,
                service.update(userId, activityId, request)));
    }

    @DeleteMapping("/{activityId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long activityId
    ) {
        service.delete(userId, activityId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.ACTIVITY_DELETED, null));
    }

    @PostMapping("/logs")
    public ResponseEntity<ApiResponse<ActivityLogResponse>> log(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ActivityLogRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.ACTIVITY_CREATED, logService.log(userId, request)));
    }

    @GetMapping("/goals/{goalCategoryId}")
    public ResponseEntity<ApiResponse<List<ActualActivityResponse>>> getOfGoal(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long goalCategoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(defaultValue = "30") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.ACTIVITIES_FOUND,
                logService.getOfGoal(userId, goalCategoryId, before, size)));
    }

    @GetMapping("/goals/{goalCategoryId}/pattern")
    public ResponseEntity<ApiResponse<RecordPatternResponse>> getPatternOfGoal(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long goalCategoryId
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.RECORD_PATTERN_FOUND,
                patternService.get(userId, goalCategoryId)));
    }

    @GetMapping("/goals/{goalCategoryId}/journal")
    public ResponseEntity<ApiResponse<RecordJournalResponse>> getJournalOfGoal(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long goalCategoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate before,
            @RequestParam(required = false) Integer days
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.RECORD_JOURNAL_FOUND,
                journalService.get(userId, goalCategoryId, before, days)));
    }
}
