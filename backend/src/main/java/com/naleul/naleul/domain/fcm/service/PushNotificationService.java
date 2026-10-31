package com.naleul.naleul.domain.fcm.service;

import com.naleul.naleul.domain.fcm.dto.request.PushNotificationRequest;
import com.naleul.naleul.domain.fcm.entity.DeviceToken;
import com.naleul.naleul.domain.fcm.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final FcmService fcmService;

    @Transactional
    public void sendToUser(PushNotificationRequest request) {
        List<DeviceToken> tokens = deviceTokenRepository.findAllByUser_UserId(request.getUserId());

        if (tokens.isEmpty()) {
            log.info("유저 {}에 등록된 FCM 토큰 없음", request.getUserId());
            return;
        }

        List<String> fcmTokens = tokens.stream()
                .map(DeviceToken::getFcmToken)
                .toList();

        // 토큰이 1개면 단건, 여러 개면 멀티캐스트
        if (fcmTokens.size() == 1) {
            fcmService.sendToDevice(fcmTokens.get(0), request.getTitle(), request.getBody(), request.getData());
        } else {
            fcmService.sendToDevices(fcmTokens, request.getTitle(), request.getBody(), request.getData());
        }
    }
}
