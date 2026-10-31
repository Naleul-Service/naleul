package com.naleul.naleul.domain.notification.entity;

import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "mission_alert_enabled", nullable = false)
    private boolean missionAlertEnabled;

    @Column(name = "mission_certification_enabled", nullable = false)
    private boolean missionCertificationEnabled;

    @Column(name = "wishlist_enabled", nullable = false)
    private boolean wishlistEnabled;

    @Column(name = "mission_progress_enabled", nullable = false)
    private boolean missionProgressEnabled;

    @Column(name = "mission_edit_enabled", nullable = false)
    private boolean missionEditEnabled;

    @Column(name = "badge_enabled", nullable = false)
    private boolean badgeEnabled;

    @Column(name = "poke_enabled", nullable = false)
    private boolean pokeEnabled;

    @Column(name = "task_alert_enabled", nullable = false)
    private boolean taskAlertEnabled;

    @Builder
    public NotificationSettings(User user) {
        this.user = user;

        // ✅ 변경 — 가입 시점 플랜에 따라 기본값을 다르게 설정
        //    Free: 전부 꺼짐 (PRO 업그레이드 전까지 알림 자체를 못 받음)
        //    Pro: 기존 정책 유지 (미션내용수정 / 뱃지획득만 기본 off)
        // BM(2026-10): 알림은 Free 도 쓸 수 있게 → 모두 Pro 기본값으로
        boolean isPremium = true;

        this.missionAlertEnabled = isPremium;
        this.missionCertificationEnabled = isPremium;
        this.wishlistEnabled = isPremium;
        this.missionProgressEnabled = isPremium;
        this.missionEditEnabled = false;   // Pro여도 원래 false였으므로 그대로
        this.badgeEnabled = false;         // Pro여도 원래 false였으므로 그대로
        this.pokeEnabled = isPremium;
        this.taskAlertEnabled = isPremium;
    }

    public boolean isEnabled(NotificationCategory category) {
        return switch (category) {
            case MISSION_ALERT -> missionAlertEnabled;
            case MISSION_CERTIFICATION -> missionCertificationEnabled;
            case WISHLIST -> wishlistEnabled;
            case MISSION_PROGRESS -> missionProgressEnabled;
            case MISSION_EDIT -> missionEditEnabled;
            case BADGE -> badgeEnabled;
            case POKE -> pokeEnabled;
        };
    }

    public void update(NotificationCategory category, boolean enabled) {
        switch (category) {
            case MISSION_ALERT -> missionAlertEnabled = enabled;
            case MISSION_CERTIFICATION -> missionCertificationEnabled = enabled;
            case WISHLIST -> wishlistEnabled = enabled;
            case MISSION_PROGRESS -> missionProgressEnabled = enabled;
            case MISSION_EDIT -> missionEditEnabled = enabled;
            case BADGE -> badgeEnabled = enabled;
            case POKE -> pokeEnabled = enabled;
        }
    }

    public void updateTaskAlertEnabled(boolean enabled) {
        this.taskAlertEnabled = enabled;
    }
}