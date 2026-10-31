package com.naleul.naleul.domain.notification.controller;

import com.naleul.naleul.domain.notification.dto.response.NotificationResponse;
import com.naleul.naleul.domain.notification.service.NotificationService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // 알림 목록 (최신순, 페이징)
    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<NotificationResponse> response = notificationService.getNotifications(userId, pageable)
                .map(NotificationResponse::from);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_FOUND, response));
    }

    // 안읽은 알림 개수 (탭바 뱃지용)
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(
            @AuthenticationPrincipal Long userId
    ) {
        Long response = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_FOUND, response));
    }

    // 알림 읽음 처리 (알림 탭했을 때 호출)
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long notificationId
    ) {
        notificationService.markAsRead(userId, notificationId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_READ, null));
    }

    // 알림 전체 읽음 처리 (알림 목록 페이지 진입 시 호출)
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal Long userId
    ) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_ALL_READ, null));
    }
}