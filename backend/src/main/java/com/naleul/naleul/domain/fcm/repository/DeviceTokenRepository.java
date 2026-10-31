package com.naleul.naleul.domain.fcm.repository;

import com.naleul.naleul.domain.fcm.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    // ✅ user 객체 자체로 탐색
    List<DeviceToken> findAllByUser_UserId(Long userId);

    boolean existsByFcmToken(String fcmToken);

    Optional<DeviceToken> findByFcmToken(String fcmToken);

    // 유효하지 않은 토큰 삭제용
    void deleteByFcmToken(String fcmToken);
}