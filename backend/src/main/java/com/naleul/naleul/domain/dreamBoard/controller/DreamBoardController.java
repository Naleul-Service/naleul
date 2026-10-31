package com.naleul.naleul.domain.dreamBoard.controller;

import com.naleul.naleul.domain.dreamBoard.dto.request.DreamBoardCreateRequest;
import com.naleul.naleul.domain.dreamBoard.dto.response.DreamBoardResponse;
import com.naleul.naleul.domain.dreamBoard.service.DreamBoardService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/dream-boards")
public class DreamBoardController {

    private final DreamBoardService dreamBoardService;

    // 드림보드 생성
    @PostMapping
    public ResponseEntity<ApiResponse<DreamBoardResponse>> create(
            @Valid @RequestBody DreamBoardCreateRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        DreamBoardResponse response = dreamBoardService.create(request, userId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.DREAM_BOARD_CREATED, response));
    }

    // 내 드림보드 카테고리별 조회 ⭐ 핵심 수정
    @GetMapping
    public ResponseEntity<ApiResponse<List<DreamBoardResponse>>> getMyDreamBoards(
            @AuthenticationPrincipal Long userId,
            @RequestParam Long goalCategoryId
    ) {
        List<DreamBoardResponse> response = dreamBoardService.getMyDreamBoards(userId, goalCategoryId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.DREAM_BOARD_FOUND, response));
    }

    // 이미지 URL 수정
    @PatchMapping("/{dreamBoardId}")
    public ResponseEntity<ApiResponse<DreamBoardResponse>> updateImageUrl(
            @PathVariable Long dreamBoardId,
            @AuthenticationPrincipal Long userId,
            @RequestParam String imageUrl
    ) {
        DreamBoardResponse response = dreamBoardService.updateImageUrl(dreamBoardId, userId, imageUrl);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.DREAM_BOARD_UPDATED, response));
    }

    // 드림보드 삭제
    @DeleteMapping("/{dreamBoardId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long dreamBoardId,
            @AuthenticationPrincipal Long userId
    ) {
        dreamBoardService.delete(dreamBoardId, userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.DREAM_BOARD_DELETED, null));
    }
}