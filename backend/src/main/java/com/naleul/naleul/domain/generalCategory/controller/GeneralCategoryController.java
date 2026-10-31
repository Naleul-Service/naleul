package com.naleul.naleul.domain.generalCategory.controller;

import com.naleul.naleul.domain.generalCategory.dto.request.GeneralCategoryCompleteRequest;
import com.naleul.naleul.domain.generalCategory.dto.request.GeneralCategoryCreateRequest;
import com.naleul.naleul.domain.generalCategory.dto.request.GeneralCategoryUpdateRequest;
import com.naleul.naleul.domain.generalCategory.dto.response.GeneralCategoryResponse;
import com.naleul.naleul.domain.generalCategory.reader.GeneralCategoryReader;
import com.naleul.naleul.domain.generalCategory.service.GeneralCategoryService;
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
@RequestMapping("/api/v1/general-categories")
public class GeneralCategoryController {

    private final GeneralCategoryService generalCategoryService;
    private final GeneralCategoryReader generalCategoryReader;

    // POST /api/general-categories
    @PostMapping
    public ResponseEntity<ApiResponse<GeneralCategoryResponse>> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GeneralCategoryCreateRequest request) {
        GeneralCategoryResponse response = generalCategoryService.create(userId, request);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.GENERAL_CREATED, response));
    }

    // GET /api/general-categories
    @GetMapping
    public ResponseEntity<ApiResponse<List<GeneralCategoryResponse>>> getAll(@AuthenticationPrincipal Long userId) {
        List<GeneralCategoryResponse> response = generalCategoryReader.getGeneralCategories(userId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.GENERALS_FOUND, response));
    }

    // GET /api/general-categories/{id}
    @GetMapping("/{generalCategoryId}")
    public ResponseEntity<ApiResponse<GeneralCategoryResponse>> getOne(
            @PathVariable Long generalCategoryId,
            @AuthenticationPrincipal Long userId) {
        GeneralCategoryResponse response = generalCategoryReader.getGeneralCategory(userId, generalCategoryId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.GENERAL_FOUND, response));
    }

    // PUT /api/general-categories/{id}
    @PutMapping("/{generalCategoryId}")
    public ResponseEntity<ApiResponse<GeneralCategoryResponse>> update(
            @PathVariable Long generalCategoryId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GeneralCategoryUpdateRequest request) {
        GeneralCategoryResponse response = generalCategoryService.update(userId, generalCategoryId, request);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.GENERAL_UPDATED, response));
    }

    // 목표 완료 처리
    @PatchMapping("/{generalCategoryId}/complete")
    public ResponseEntity<ApiResponse<GeneralCategoryResponse>> complete(
            @PathVariable Long generalCategoryId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GeneralCategoryCompleteRequest request
    ) {
        GeneralCategoryResponse response = generalCategoryService.complete(generalCategoryId, userId,  request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.GENERAL_COMPLETED, response));
    }

    // DELETE /api/general-categories/{id}
    @DeleteMapping("/{generalCategoryId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long generalCategoryId,
            @AuthenticationPrincipal Long userId) {
        generalCategoryService.delete(userId, generalCategoryId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.GENERAL_DELETED, null));
    }
}