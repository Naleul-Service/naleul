package com.naleul.naleul.domain.fcm.entity;

import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true)
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;  // IOS, ANDROID

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum Platform {
        IOS, ANDROID
    }

    @Builder
    public DeviceToken(User user, String fcmToken, Platform platform) {
        this.user = user;
        this.fcmToken = fcmToken;
        this.platform = platform;
        this.createdAt = LocalDateTime.now();
    }

    public static DeviceToken of(User user, String fcmToken, Platform platform) {
        return DeviceToken.builder()
                .user(user)
                .fcmToken(fcmToken)
                .platform(platform)
                .build();
    }

    // 토큰의 주인이 바뀌었을 때 업데이트하는 메서드
    public void updateOwner(User user, Platform platform) {
        this.user = user;
        this.platform = platform;
    }

    public void updateUserAndPlatform(User user, Platform platform) {
        this.user = user;
        this.platform = platform;
    }
}