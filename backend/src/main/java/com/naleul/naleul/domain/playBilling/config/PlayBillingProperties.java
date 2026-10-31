package com.naleul.naleul.domain.playBilling.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.yml 의 google.play.* — Android Google Play 구독 확인.
 *
 * Play Console → 설정 → API 액세스에서 Google Cloud 서비스 계정을 연결하고 "재무 데이터 보기 · 주문 및 구독 관리" 권한을 준 뒤,
 * 그 서비스 계정의 JSON 키 파일을 서버에 두고 경로를 적어요. 키 파일은 서버에만 두고 깃에 올리지 않아요.
 *
 * @param packageName        Android 앱 패키지 이름
 * @param serviceAccountPath 서비스 계정 JSON 키 파일 경로. 비우면 Play 결제 확인 API 가 "준비 중"으로 응답해요 (기동은 됨)
 * @param rtdnToken          Play 실시간 알림(Pub/Sub push) 주소 끝에 붙이는 비밀 값 (…/rtdn?token=값). 다르면 무시해요
 */
@ConfigurationProperties(prefix = "google.play")
public record PlayBillingProperties(
        @DefaultValue("com.naleul.naleul") String packageName,
        @DefaultValue("") String serviceAccountPath,
        @DefaultValue("") String rtdnToken
) {
    public boolean configured() {
        return serviceAccountPath != null && !serviceAccountPath.isBlank();
    }
}
