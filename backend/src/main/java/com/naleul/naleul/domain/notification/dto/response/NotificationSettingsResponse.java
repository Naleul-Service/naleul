package com.naleul.naleul.domain.notification.dto.response;

import com.naleul.naleul.domain.notification.entity.NotificationSettings;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationSettingsResponse {
    private boolean missionAlertEnabled;
    private boolean missionCertificationEnabled;
    private boolean wishlistEnabled;
    private boolean missionProgressEnabled;
    private boolean missionEditEnabled;
    private boolean badgeEnabled;
    private boolean pokeEnabled;
    private boolean taskAlertEnabled;

    public static NotificationSettingsResponse from(NotificationSettings s) {
        return NotificationSettingsResponse.builder()
                .missionAlertEnabled(s.isMissionAlertEnabled())
                .missionCertificationEnabled(s.isMissionCertificationEnabled())
                .wishlistEnabled(s.isWishlistEnabled())
                .missionProgressEnabled(s.isMissionProgressEnabled())
                .missionEditEnabled(s.isMissionEditEnabled())
                .badgeEnabled(s.isBadgeEnabled())
                .pokeEnabled(s.isPokeEnabled())
                .taskAlertEnabled(s.isTaskAlertEnabled())
                .build();
    }
}