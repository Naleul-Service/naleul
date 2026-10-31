package com.naleul.naleul.domain.fcm.dto.request;

import com.naleul.naleul.domain.fcm.entity.DeviceToken;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 토큰 등록 요청
@Getter
@NoArgsConstructor
public class DeviceTokenRequest {
    private String fcmToken;
    private DeviceToken.Platform platform;  // "IOS" or "ANDROID"
}
