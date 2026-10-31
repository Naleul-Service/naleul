package com.naleul.naleul.domain.notification.dto.response;

import com.naleul.naleul.domain.notification.entity.Notification;
import com.naleul.naleul.domain.notification.entity.NotificationActionType;
import com.naleul.naleul.domain.notification.entity.NotificationCategory;
import com.naleul.naleul.domain.notification.entity.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationResponse {
    private Long id;
    private NotificationCategory category;
    private NotificationType type;   // ✅ 추가 — 이미 엔티티에 있는 값을 그대로 내려주기만 하면 됨 (마이그레이션 불필요)
    private String message;
    private NotificationActionType actionType;
    private Long targetId;
    private boolean read;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .category(n.getCategory())
                .type(n.getType())   // ✅ 추가
                .message(n.getMessage())
                .actionType(n.getActionType())
                .targetId(n.getTargetId())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}