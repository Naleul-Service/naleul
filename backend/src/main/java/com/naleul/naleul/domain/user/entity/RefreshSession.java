package com.naleul.naleul.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 로그인 한 번 = 한 줄 (웹, iPhone, iPad 가 각자 따로). V54.
 * 재발급할 때 이 줄의 토큰만 바꾸므로 다른 기기의 로그인에 영향을 주지 않는다.
 */
@Entity
@Table(name = "refresh_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_session_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(nullable = false, length = 512)
    private String token;

    @Column(name = "previous_token_hash", length = 64, columnDefinition = "CHAR(64)")
    private String previousTokenHash;

    @Column(name = "rotated_at")
    private LocalDateTime rotatedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at", nullable = false)
    private LocalDateTime lastUsedAt;

    public static RefreshSession start(Long userId, String token, String tokenHash,
                                       LocalDateTime now, LocalDateTime expiresAt) {
        RefreshSession s = new RefreshSession();
        s.userId = userId;
        s.token = token;
        s.tokenHash = tokenHash;
        s.createdAt = now;
        s.lastUsedAt = now;
        s.expiresAt = expiresAt;
        return s;
    }

    public void rotate(String newToken, String newHash, LocalDateTime now, LocalDateTime newExpiresAt) {
        this.previousTokenHash = this.tokenHash;
        this.token = newToken;
        this.tokenHash = newHash;
        this.rotatedAt = now;
        this.lastUsedAt = now;
        this.expiresAt = newExpiresAt;
    }

    public void touch(LocalDateTime now) {
        this.lastUsedAt = now;
    }
}
