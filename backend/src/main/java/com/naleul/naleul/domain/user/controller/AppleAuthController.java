package com.naleul.naleul.domain.user.controller;

import com.naleul.naleul.domain.user.dto.request.AppleLoginRequest;
import com.naleul.naleul.domain.user.dto.LoginResponse;
import com.naleul.naleul.domain.user.service.AppleAuthService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/apple")
@RequiredArgsConstructor
public class AppleAuthController {

    private final AppleAuthService appleAuthService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> appleLogin(@RequestBody AppleLoginRequest request) {
        LoginResponse response = appleAuthService.appleLogin(request);
        return ApiResponse.success(SuccessCode.LOGIN_SUCCESS, response);
    }
}