package com.naleul.naleul.domain.fcm.dto.request;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

// 푸시 발송 요청 (서버 내부용)
@Getter
@Builder
public class PushNotificationRequest {
    private Long userId;
    private String title;
    private String body;
    private Map<String, String> data;  // 딥링크, 화면 이동 등 커스텀 데이터
}
