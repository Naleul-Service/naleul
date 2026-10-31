package com.naleul.naleul.domain.mission.dto.response;

import com.naleul.naleul.domain.mission.entity.MissionBadge;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MissionBadgeResponse {

    private Long missionBadgeId;
    private Long missionId;
    private String badgeName;
    private String badgeType;
    private String categoryItem;     // ✅ 변경 — ITEM_PUZZLE 아니면 null
    private int level;
    private Double achievementRate;
    private LocalDateTime acquiredAt;

    public static MissionBadgeResponse from(MissionBadge badge) {
        return MissionBadgeResponse.builder()
                .missionBadgeId(badge.getMissionBadgeId())
                .missionId(badge.getMission() != null ? badge.getMission().getMissionId() : null)
                .badgeName(badge.getBadgeName())
                .badgeType(badge.getBadgeType().name())
                .categoryItem(badge.getCategoryItem() != null ? badge.getCategoryItem().name() : null)
                .level(badge.getLevel())
                .achievementRate(badge.getAchievementRate())
                .acquiredAt(badge.getAcquiredAt())
                .build();
    }
}