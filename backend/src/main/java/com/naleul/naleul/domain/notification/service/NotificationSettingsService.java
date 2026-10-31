package com.naleul.naleul.domain.notification.service;

import com.naleul.naleul.domain.notification.dto.response.NotificationSettingsResponse;
import com.naleul.naleul.domain.notification.entity.NotificationCategory;
import com.naleul.naleul.domain.notification.entity.NotificationSettings;
import com.naleul.naleul.domain.notification.repository.NotificationSettingsRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationSettingsService {

    private final NotificationSettingsRepository notificationSettingsRepository;
    private final UserFinder userFinder;

    // ⭐ readOnly 제거 — getOrCreate가 내부에서 save()를 할 수 있으므로 쓰기 가능한 트랜잭션이어야 함
    public NotificationSettingsResponse get(Long userId) {
        NotificationSettings settings = getOrCreate(userId);
        return NotificationSettingsResponse.from(settings);
    }

    private NotificationSettings getOrCreate(Long userId) {
        return notificationSettingsRepository.findByUser_UserId(userId)
                .orElseGet(() -> {
                    User user = userFinder.findUser(userId);
                    return notificationSettingsRepository.save(
                            NotificationSettings.builder().user(user).build()
                    );
                });
    }

    public NotificationSettingsResponse update(Long userId, NotificationCategory category, boolean enabled) {
        User user = userFinder.findUser(userId);
        validateNotificationSettingsAccess(user, enabled);
        NotificationSettings settings = getOrCreate(userId);
        settings.update(category, enabled);
        return NotificationSettingsResponse.from(settings);
    }

    public NotificationSettingsResponse updateTaskAlert(Long userId, boolean enabled) {
        User user = userFinder.findUser(userId);
        validateNotificationSettingsAccess(user, enabled);
        NotificationSettings settings = getOrCreate(userId);
        settings.updateTaskAlertEnabled(enabled);
        return NotificationSettingsResponse.from(settings);
    }

    /** BM(2026-10): 알림은 돈이 들지 않아 Free 도 모두 쓸 수 있다. 다시 Pro 전용으로 돌릴 때를 위해 자리만 남김 */
    private void validateNotificationSettingsAccess(User user, boolean enabled) {
    }
}