package com.naleul.naleul.domain.user.controller;

import com.naleul.naleul.domain.user.dto.LoginResponse;
import com.naleul.naleul.domain.user.service.GuestAccountService;
import com.naleul.naleul.domain.user.service.KakaoAuthService;
import com.naleul.naleul.domain.user.service.RefreshSessionService;
import com.naleul.naleul.domain.user.service.TokenReissueService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KakaoAuthService kakaoAuthService;
    private final TokenReissueService tokenReissueService;
    private final RefreshSessionService refreshSessionService;
    private final GuestAccountService guestAccountService;

    @GetMapping("/kakao/callback")
    public ResponseEntity<ApiResponse<LoginResponse>> kakaoCallback(
            @RequestParam String code,
            @RequestParam(required = false) String redirectUri
    ) {
        LoginResponse response = kakaoAuthService.kakaoLogin(code, redirectUri);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(SuccessCode.LOGIN_SUCCESS, response));
    }

    // iOS용 카카오 로그인 — accessToken 직접 전달
    @GetMapping("/kakao/ios")
    public ResponseEntity<ApiResponse<LoginResponse>> kakaoIosLogin(
            @RequestParam String accessToken
    ) {
        LoginResponse response = kakaoAuthService.kakaoLoginWithAccessToken(accessToken);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(SuccessCode.LOGIN_SUCCESS, response));
    }

    @GetMapping("/token-reissue")
    public ResponseEntity<ApiResponse<LoginResponse>> reissueToken(
            @RequestHeader("Authorization-Refresh") String authHeader  // "Bearer {refreshToken}"
    ) {
        // "Bearer " 제거
        String refreshToken = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        LoginResponse response = tokenReissueService.reissue(refreshToken);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(SuccessCode.LOGIN_SUCCESS, response));
    }

    /**
     * 로그인 없이 둘러보기 — 읽기 전용 토큰 (refreshToken 없음, 30일).
     * 저장하려고 하면 401 GUEST_LOGIN_REQUIRED → 앱 · 웹이 로그인 안내를 띄운다.
     */
    @PostMapping("/guest")
    public ResponseEntity<ApiResponse<LoginResponse>> guest() {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.LOGIN_SUCCESS, guestAccountService.issue()));
    }

    /** 로그아웃 — 이 기기의 refresh token 만 무효화 (다른 기기 로그인은 그대로) */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization-Refresh", required = false) String authHeader
    ) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            refreshSessionService.revoke(authHeader.substring(7));
        }
        return ResponseEntity.noContent().build();
    }
}
