package com.naleul.naleul.domain.userColor.controller;

import com.naleul.naleul.domain.userColor.dto.request.UserColorAddRequest;
import com.naleul.naleul.domain.userColor.dto.response.UserColorResponse;
import com.naleul.naleul.domain.userColor.service.UserColorService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/user-colors")
public class UserColorController {

    private final UserColorService userColorService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserColorResponse>>> getUserColors(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.COLORS_FOUND, userColorService.getUserColors(userId))
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserColorResponse>> addColor(
            @AuthenticationPrincipal Long userId,
            @RequestBody UserColorAddRequest request
    ) {
        UserColorResponse response = userColorService.addColorToUser(userId, request.getColorCode());
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.COLOR_CREATED, response)
        );
    }

    @DeleteMapping("/{userColorId}")
    public ResponseEntity<ApiResponse<Void>> deleteUserColor(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long userColorId
    ) {
        userColorService.deleteUserColor(userId, userColorId);
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.COLOR_DELETED, null)
        );
    }
}