package com.naleul.naleul.domain.user.controller;

import com.naleul.naleul.domain.chart.dto.request.UserPatternTypeUpdateRequest;
import com.naleul.naleul.domain.chart.dto.response.UserPatternTypeResponse;
import com.naleul.naleul.domain.user.dto.request.UserNicknameUpdateRequest;
import com.naleul.naleul.domain.user.dto.request.UserWithdrawalRequest;
import com.naleul.naleul.domain.user.dto.response.UserNicknameUpdateResponse;
import com.naleul.naleul.domain.user.dto.response.UserResponse;
import com.naleul.naleul.domain.user.service.UserService;
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
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(SuccessCode.USERS_FOUND, userService.getAllUsers()));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long userId) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(SuccessCode.USER_FOUND, userService.getUserById(userId)));
    }

    @PatchMapping("/nickname")
    public ResponseEntity<ApiResponse<UserNicknameUpdateResponse>> updateNickname(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserNicknameUpdateRequest request
    ) {
        UserNicknameUpdateResponse response = userService.updateNickname(userId, request.nickname());
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.USER_NICKNAME_UPDATED, response)
        );
    }

    @PatchMapping("/pattern-type")
    public ResponseEntity<ApiResponse<UserPatternTypeResponse>> updatePatternType(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserPatternTypeUpdateRequest request
    ) {
        UserPatternTypeResponse response = userService.updatePatternType(userId, request.patternType());
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.USER_PATTERN_TYPE_UPDATED, response)
        );
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserWithdrawalRequest request
    ) {
        userService.withdraw(userId, request);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.USER_WITHDRAWN, null));
    }
}
