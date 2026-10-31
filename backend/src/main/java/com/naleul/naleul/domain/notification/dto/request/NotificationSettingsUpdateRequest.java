package com.naleul.naleul.domain.notification.dto.request;

import com.naleul.naleul.domain.notification.entity.NotificationCategory;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class NotificationSettingsUpdateRequest {
    private NotificationCategory category;
    private boolean enabled;
}