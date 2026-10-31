// retrospective/controller/RetrospectiveController.java
package com.naleul.naleul.domain.retrospective.controller;

import com.naleul.naleul.domain.retrospective.dto.request.RetrospectiveCreateRequest;
import com.naleul.naleul.domain.retrospective.dto.request.RetrospectiveUpdateRequest;
import com.naleul.naleul.domain.retrospective.dto.response.RetrospectiveResponse;
import com.naleul.naleul.domain.retrospective.enums.ReviewType;
import com.naleul.naleul.domain.retrospective.service.RetrospectiveService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/retrospectives")
public class RetrospectiveController {

    private final RetrospectiveService retrospectiveService;

    // 생성
    @PostMapping
    public ResponseEntity<ApiResponse<RetrospectiveResponse>> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RetrospectiveCreateRequest request
    ) {
        RetrospectiveResponse response = retrospectiveService.create(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.RETROSPECTIVE_CREATED, response));
    }

    // 단건 조회
    @GetMapping("/{retrospectiveId}")
    public ResponseEntity<ApiResponse<RetrospectiveResponse>> getOne(
            @PathVariable Long retrospectiveId
    ) {
        RetrospectiveResponse response = retrospectiveService.getOne(retrospectiveId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.RETROSPECTIVE_FOUND, response));
    }

    // 목록 조회 (필터 + 페이지네이션)
    // 쿼리 파라미터:
    //   reviewType  = DAILY | WEEKLY | MONTHLY  (없으면 전체)
    //   baseDate    = 2025-05-01                (없으면 오늘 기준)
    //   goalCategoryId, generalCategoryId       (없으면 필터 없음)
    @GetMapping
    public ResponseEntity<ApiResponse<Page<RetrospectiveResponse>>> getList(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) ReviewType reviewType,
            @RequestParam(required = false) LocalDate baseDate,
            @RequestParam(required = false) Long goalCategoryId,
            @RequestParam(required = false) Long generalCategoryId,
            @PageableDefault(size = 10, sort = "reviewDate", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        Page<RetrospectiveResponse> response = retrospectiveService.getList(
                userId, reviewType, baseDate, goalCategoryId, generalCategoryId, pageable
        );
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.RETROSPECTIVE_FOUND, response));
    }

    // 수정
    @PatchMapping("/{retrospectiveId}")
    public ResponseEntity<ApiResponse<RetrospectiveResponse>> update(
            @AuthenticationPrincipal Long userId,          // 추가
            @PathVariable Long retrospectiveId,
            @Valid @RequestBody RetrospectiveUpdateRequest request
    ) {
        RetrospectiveResponse response = retrospectiveService.update(userId, retrospectiveId, request);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.RETROSPECTIVE_UPDATED, response));
    }

    @DeleteMapping("/{retrospectiveId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Long userId,          // 추가
            @PathVariable Long retrospectiveId
    ) {
        retrospectiveService.delete(userId, retrospectiveId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.RETROSPECTIVE_DELETED, null));
    }
}