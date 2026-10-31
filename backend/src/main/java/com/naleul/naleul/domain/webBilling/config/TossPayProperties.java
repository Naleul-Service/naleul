package com.naleul.naleul.domain.webBilling.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * application.yml 의 toss.pay.* — 토스페이 정기결제 (pay.toss.im, 토스페이먼츠와 다른 계약 · 다른 키).
 *
 * 키는 토스페이 가맹점 계약 후 받아요. 계약 전에는 토스페이 연동 가이드의 테스트 키(sk_test_…)로 테스트할 수 있어요.
 * 결제 도메인은 운영 하나뿐이고, 실제 돈이 나가는지는 키(sk_test_ / sk_live_)로 정해져요.
 * API 키는 서버에만 두고 절대 웹에 노출하지 않아요.
 *
 * @param apiKey         비우면 웹의 '토스페이 정기구독'이 "준비 중"으로 보여요 (기동은 됨)
 * @param callbackUrl    토스가 빌링키 활성화 · 삭제를 알려주는 이 서버 주소 (…/api/v1/web-billing/toss-pay/callback). 80 · 443 포트만
 * @param returnOrigins  인증 후 돌아갈 웹 주소로 허용하는 origin (웹이 보낸 origin 이 여기 있을 때만 써요)
 */
@ConfigurationProperties(prefix = "toss.pay")
public record TossPayProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("https://pay.toss.im") String apiBaseUrl,
        @DefaultValue("") String callbackUrl,
        @DefaultValue({"http://localhost:3000", "https://naleul.vercel.app", "https://naleul.com", "https://www.naleul.com"})
        List<String> returnOrigins
) {
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank() && callbackUrl != null && !callbackUrl.isBlank();
    }

    /** 테스트 키면 실제 돈이 나가지 않아요 */
    public boolean testMode() {
        return configured() && apiKey.startsWith("sk_test_");
    }
}
