package com.naleul.naleul.domain.webBilling.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.yml 의 toss.payments.* — 웹 구독 결제(토스페이먼츠 자동결제).
 *
 * 키는 개발자센터(developers.tosspayments.com) → API 키 메뉴에서 받아요.
 *  - 테스트 키(test_ck_ / test_sk_): 계약 없이 바로 쓸 수 있고 실제 돈이 나가지 않아요
 *  - 운영 키(live_): 자동결제 계약 · 심사 후. 키만 바꾸면 그대로 동작해요
 * 시크릿 키는 서버에만 두고 절대 웹에 노출하지 않아요 (웹은 NEXT_PUBLIC_TOSS_CLIENT_KEY 만).
 *
 * @param secretKey    비우면 웹 결제 API 가 "준비 중"으로 응답해요 (기동은 됨)
 * @param monthlyPrice Pro 월간 (원, 부가세 포함)
 * @param yearlyPrice  Pro 연간 (원, 부가세 포함)
 */
@ConfigurationProperties(prefix = "toss.payments")
public record TossPaymentsProperties(
        @DefaultValue("") String secretKey,
        @DefaultValue("https://api.tosspayments.com") String apiBaseUrl,
        @DefaultValue("3300") long monthlyPrice,
        @DefaultValue("33000") long yearlyPrice
) {
    /**
     * 환경 변수에 섞여 들어간 공백 · 줄바꿈 · 따옴표를 떼요 (2026-10-28).
     * "test_sk_…\n" 이나 "\"test_sk_…\"" 처럼 들어가면 토스가 401 을 돌려줘요.
     */
    public TossPaymentsProperties {
        if (secretKey != null) {
            secretKey = secretKey.strip();
            if (secretKey.length() >= 2 && (secretKey.startsWith("\"") && secretKey.endsWith("\"")
                    || secretKey.startsWith("'") && secretKey.endsWith("'"))) {
                secretKey = secretKey.substring(1, secretKey.length() - 1).strip();
            }
        }
    }

    /** 로그용 — 키 종류만 (test_sk / live_sk / test_gsk 위젯 키 …) · 길이. 키 값은 남기지 않아요 */
    public String keyKind() {
        if (!configured()) return "없음";
        int i = secretKey.indexOf('_', secretKey.indexOf('_') + 1);
        String kind = i > 0 ? secretKey.substring(0, i) : "알 수 없는 형식";
        return kind + " · " + secretKey.length() + "자";
    }

    public boolean configured() {
        return secretKey != null && !secretKey.isBlank();
    }

    /** 테스트 키면 실제 돈이 나가지 않아요 */
    public boolean testMode() {
        return configured() && secretKey.startsWith("test_");
    }
}
