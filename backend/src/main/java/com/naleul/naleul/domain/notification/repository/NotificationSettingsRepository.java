package com.naleul.naleul.domain.notification.repository;

import com.naleul.naleul.domain.notification.entity.NotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Long> {
    Optional<NotificationSettings> findByUser_UserId(Long userId);
    List<NotificationSettings> findAllByUser_UserIdIn(Set<Long> userIds);
}