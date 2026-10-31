package com.naleul.naleul.domain.mission.entity;

import com.naleul.naleul.domain.mission.enums.MissionBadgeType;
import com.naleul.naleul.domain.mission.enums.MissionCategoryItem;
import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "mission_badges")
@Getter
@NoArgsConstructor
public class MissionBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long missionBadgeId;

    @Column(nullable = false, length = 100)
    private String badgeName;

    // ✅ imageUrl 필드 제거 — 이미지는 iOS에서 badgeType 기준으로 로컬 처리

    @Column
    private Double achievementRate;

    @Column(nullable = false)
    private LocalDateTime acquiredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MissionBadgeType badgeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_item", length = 20)
    private MissionCategoryItem categoryItem;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private boolean isConfirmed = false; // 뱃지 획득 모달을 확인했는지

    public void confirm() {
        this.isConfirmed = true;
    }

    @Builder
    public MissionBadge(String badgeName, Double achievementRate, Mission mission, User user,
                        MissionBadgeType badgeType, MissionCategoryItem categoryItem, Integer level) {
        this.badgeName = badgeName;
        this.achievementRate = achievementRate;
        this.acquiredAt = LocalDateTime.now();
        this.mission = mission;
        this.user = user;
        this.badgeType = badgeType;
        this.categoryItem = categoryItem;
        this.level = (level != null) ? level : 1;
    }

    public void levelUp() {
        this.level += 1;
    }
}