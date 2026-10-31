package com.naleul.naleul.domain.notification.service;

import com.naleul.naleul.domain.fcm.dto.request.PushNotificationRequest;
import com.naleul.naleul.domain.fcm.service.PushNotificationService;
import com.naleul.naleul.domain.notification.entity.*;
import com.naleul.naleul.domain.notification.repository.NotificationRepository;
import com.naleul.naleul.domain.notification.repository.NotificationSettingsRepository;
import com.naleul.naleul.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final PushNotificationService pushNotificationService;

    /**
     * 알림 생성 + (필요시) 푸시 발송을 한번에 처리하는 진입점.
     * 다른 도메인(미션 신청, 인증, 찜, 뱃지 등) 서비스에서는 이 메서드만 호출하면 됨.
     *
     * ✅ 변경 — 유저의 카테고리별 on/off 설정은 "푸시 발송 여부"만 제어함.
     *    인앱 히스토리(Notification 저장)는 설정과 무관하게 항상 저장됨.
     */
    @Transactional
    public void notify(User user, NotificationType type, Long targetId, Object... args) {
        String message = String.format(type.getMessageTemplate(), args);

        // 1) 인앱 히스토리 저장 — 설정 on/off와 무관하게 항상 저장
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .message(message)
                .targetId(targetId)
                .build();
        notificationRepository.save(notification);

        // 2) 이 카테고리가 애초에 푸시 대상 카테고리이면서(pushEnabledByDefault),
        //    유저가 해당 카테고리 푸시를 켜놨을 때만 FCM 발송
        NotificationSettings settings = getOrCreateSettings(user);
        boolean pushAllowed = type.getCategory().isPushEnabledByDefault()
                && settings.isEnabled(type.getCategory());

        if (pushAllowed) {
            // ✅ 변경 — actionType만으로는 같은 값이 여러 화면으로 갈리는 경우가 있어서
            // (AlarmView.swift의 NotificationRoute 주석 참고), 더 구체적인 type을 실어 보냄.
            // iOS는 이 값으로 NotificationRoute(type:targetId:)를 그대로 재사용해서 라우팅함.
            Map<String, String> data = new HashMap<>();
            data.put("type", type.name());
            if (targetId != null) {
                data.put("targetId", String.valueOf(targetId));
            }

            PushNotificationRequest request = PushNotificationRequest.builder()
                    .userId(user.getUserId())
                    .title("나를")
                    .body(message)
                    .data(data)   // ⚠️ PushNotificationRequest에 이 필드를 추가해야 함 (아래 안내 참고)
                    .build();
            pushNotificationService.sendToUser(request);
        } else {
            log.info("유저 {} - {} 카테고리 푸시 스킵 (설정 꺼짐 또는 푸시비대상 카테고리)",
                    user.getUserId(), type.getCategory());
        }
    }

    @Transactional(readOnly = true)
    public Page<Notification> getNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findAllByUser_UserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUser_UserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다."));

        if (!notification.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 알림만 읽음 처리할 수 있습니다.");
        }
        notification.markAsRead();
    }

    private NotificationSettings getOrCreateSettings(User user) {
        return notificationSettingsRepository.findByUser_UserId(user.getUserId())
                .orElseGet(() -> notificationSettingsRepository.save(
                        NotificationSettings.builder().user(user).build()
                ));
    }

    // 전체 읽음 처리
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }
}