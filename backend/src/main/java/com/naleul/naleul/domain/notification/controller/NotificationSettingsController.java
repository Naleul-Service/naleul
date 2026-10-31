package com.naleul.naleul.domain.notification.controller;

import com.naleul.naleul.domain.notification.dto.request.NotificationSettingsUpdateRequest;
import com.naleul.naleul.domain.notification.dto.request.TaskAlertUpdateRequest;
import com.naleul.naleul.domain.notification.dto.response.NotificationSettingsResponse;
import com.naleul.naleul.domain.notification.service.NotificationSettingsService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notification-settings")
@RequiredArgsConstructor
public class NotificationSettingsController {

    private final NotificationSettingsService notificationSettingsService;

    // 설정 화면 진입 시 현재 토글 상태 조회
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> get(
            @AuthenticationPrincipal Long userId
    ) {
        NotificationSettingsResponse response = notificationSettingsService.get(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_SETTINGS_FOUND, response));
    }

    // 토글 하나 켜고/끌 때마다 호출 (카테고리 단위)
    @PatchMapping
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> update(
            @AuthenticationPrincipal Long userId,
            @RequestBody NotificationSettingsUpdateRequest request
    ) {
        NotificationSettingsResponse response = notificationSettingsService.update(
                userId, request.getCategory(), request.isEnabled()
        );
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_SETTINGS_UPDATED, response));
    }

    // Task 푸시 알림 on/off (일정 푸시 알림 화면 전용 — 히스토리에는 안 남음)
    @PatchMapping("/task-alert")
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> updateTaskAlert(
            @AuthenticationPrincipal Long userId,
            @RequestBody TaskAlertUpdateRequest request
    ) {
        NotificationSettingsResponse response = notificationSettingsService.updateTaskAlert(userId, request.isEnabled());
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_SETTINGS_UPDATED, response));
    }
}