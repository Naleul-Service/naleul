package com.naleul.naleul.domain.ideaBoard.controller;

import com.naleul.naleul.domain.ideaBoard.dto.request.IdeaBoardCreateRequest;
import com.naleul.naleul.domain.ideaBoard.dto.request.IdeaBoardUpdateRequest;
import com.naleul.naleul.domain.ideaBoard.dto.response.IdeaBoardResponse;
import com.naleul.naleul.domain.ideaBoard.enums.IdeaStatus;
import com.naleul.naleul.domain.ideaBoard.service.IdeaBoardService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/idea-boards")
public class IdeaBoardController {

    private final IdeaBoardService ideaBoardService;

    // 아이디어 생성
    @PostMapping
    public ResponseEntity<ApiResponse<IdeaBoardResponse>> create(
            @Valid @RequestBody IdeaBoardCreateRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        IdeaBoardResponse response = ideaBoardService.create(request, userId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.IDEA_CREATED, response));
    }

    // 내 아이디어 카테고리별 조회 ⭐ 핵심 수정
    @GetMapping
    public ResponseEntity<ApiResponse<List<IdeaBoardResponse>>> getMyIdeas(
            @AuthenticationPrincipal Long userId,
            @RequestParam Long goalCategoryId
    ) {
        List<IdeaBoardResponse> response = ideaBoardService.getMyIdeas(userId, goalCategoryId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.IDEA_FOUND, response));
    }

    // 상태별 필터 조회 ⭐ 핵심 수정
    @GetMapping("/filter")
    public ResponseEntity<ApiResponse<List<IdeaBoardResponse>>> getMyIdeasByStatus(
            @AuthenticationPrincipal Long userId,
            @RequestParam Long goalCategoryId,
            @RequestParam IdeaStatus status
    ) {
        List<IdeaBoardResponse> response = ideaBoardService.getMyIdeasByStatus(userId, goalCategoryId, status);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.IDEA_FOUND, response));
    }

    // 페이징 조회 ⭐ 핵심 수정
    @GetMapping("/paged")
    public ResponseEntity<ApiResponse<Page<IdeaBoardResponse>>> getMyIdeasPaged(
            @AuthenticationPrincipal Long userId,
            @RequestParam Long goalCategoryId,
            Pageable pageable
    ) {
        Page<IdeaBoardResponse> response = ideaBoardService.getMyIdeasPaged(userId, goalCategoryId, pageable);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.IDEA_FOUND, response));
    }

    // 아이디어 수정 — 변경 없음
    @PatchMapping("/{ideaBoardId}")
    public ResponseEntity<ApiResponse<IdeaBoardResponse>> update(
            @PathVariable Long ideaBoardId,
            @AuthenticationPrincipal Long userId,
            @RequestBody IdeaBoardUpdateRequest request
    ) {
        IdeaBoardResponse response = ideaBoardService.update(ideaBoardId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.IDEA_UPDATED, response));
    }

    // 아이디어 삭제 — 변경 없음
    @DeleteMapping("/{ideaBoardId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long ideaBoardId,
            @AuthenticationPrincipal Long userId
    ) {
        ideaBoardService.delete(ideaBoardId, userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.IDEA_DELETED, null));
    }
}