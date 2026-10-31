package com.naleul.naleul.domain.fcm.controller;

import com.naleul.naleul.domain.fcm.dto.request.DeviceTokenRequest;
import com.naleul.naleul.domain.fcm.service.DeviceTokenService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/device-token")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> register(
            @AuthenticationPrincipal Long userId,
            @RequestBody DeviceTokenRequest request
    ) {
        deviceTokenService.save(userId, request);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.DEVICE_TOKEN_SAVED));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> unregister(
            @RequestBody DeviceTokenRequest request
    ) {
        deviceTokenService.delete(request.getFcmToken());
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.DEVICE_TOKEN_DELETED));
    }
}