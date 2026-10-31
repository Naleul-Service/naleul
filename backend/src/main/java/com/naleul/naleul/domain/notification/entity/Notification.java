package com.naleul.naleul.domain.notification.entity;

import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 수신자

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false, length = 500)
    private String message; // 템플릿에 값 채워서 저장된 최종 문구

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationActionType actionType;

    @Column(name = "target_id")
    private Long targetId; // 미션 id, 뱃지 id 등 딥링크에 필요한 대상 id

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public Notification(User user, NotificationType type, String message, Long targetId) {
        this.user = user;
        this.type = type;
        this.category = type.getCategory();
        this.message = message;
        this.actionType = type.getActionType();
        this.targetId = targetId;
        this.read = false;
        this.createdAt = LocalDateTime.now();
    }

    public void markAsRead() {
        this.read = true;
    }
}