package com.naleul.naleul.domain.fcm.service;

import com.naleul.naleul.domain.fcm.dto.request.DeviceTokenRequest;
import com.naleul.naleul.domain.fcm.entity.DeviceToken;
import com.naleul.naleul.domain.fcm.repository.DeviceTokenRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.repository.UserRepository;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;
    private final UserFinder userFinder;

    public void save(Long userId, DeviceTokenRequest request) {
        User user = userFinder.findUser(userId);

        deviceTokenRepository.findByFcmToken(request.getFcmToken())
                .ifPresentOrElse(
                        existing -> existing.updateOwner(user, request.getPlatform()),
                        () -> deviceTokenRepository.save(
                                DeviceToken.of(user, request.getFcmToken(), request.getPlatform())
                        )
                );
    }

    public void delete(String fcmToken) {
        deviceTokenRepository.deleteByFcmToken(fcmToken);
    }
}