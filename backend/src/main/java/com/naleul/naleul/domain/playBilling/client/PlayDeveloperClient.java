package com.naleul.naleul.domain.playBilling.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.naleul.naleul.domain.playBilling.config.PlayBillingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Google Play Developer API (androidpublisher v3) — 구독 조회 · 확인 처리.
 *  - GET  /applications/{pkg}/purchases/subscriptionsv2/tokens/{token}             구독 상태
 *  - POST /applications/{pkg}/purchases/subscriptions/{productId}/tokens/{token}:acknowledge  확인 처리
 * 확인 처리를 3일 안에 안 하면 Google 이 자동 환불해요. 서버가 Pro 를 준 뒤 바로 해요.
 * 인증: 서비스 계정(JSON 키) → OAuth 토큰 (scope androidpublisher). 토큰은 만료 전까지 다시 쓴다.
 */
@Slf4j
@Component
public class PlayDeveloperClient {

    private static final String SCOPE = "https://www.googleapis.com/auth/androidpublisher";
    private static final String BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3";
    /** Pro 만료 비교(User.isCurrentlyPremium)가 JVM 시간대 LocalDateTime 이라 같은 기준으로 */
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final PlayBillingProperties properties;
    private final RestClient restClient;
    private volatile GoogleCredentials credentials;

    public PlayDeveloperClient(PlayBillingProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.restClient = RestClient.builder().baseUrl(BASE).requestFactory(factory).build();
    }

    // ── 응답 (필요한 칸만) ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubscriptionPurchaseV2(String subscriptionState, String acknowledgementState, String latestOrderId,
                                         String linkedPurchaseToken, Object testPurchase,
                                         ExternalAccountIdentifiers externalAccountIdentifiers,
                                         List<LineItem> lineItems) {
        public boolean acknowledged() {
            return "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED".equals(acknowledgementState);
        }

        public boolean isTest() {
            return testPurchase != null;
        }

        public String obfuscatedAccountId() {
            return externalAccountIdentifiers != null ? externalAccountIdentifiers.obfuscatedExternalAccountId() : null;
        }

        /** 구독 상품 하나라 첫 줄 */
        public LineItem line() {
            return lineItems != null && !lineItems.isEmpty() ? lineItems.get(0) : null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExternalAccountIdentifiers(String obfuscatedExternalAccountId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LineItem(String productId, String expiryTime, AutoRenewingPlan autoRenewingPlan,
                           OfferDetails offerDetails) {
        public LocalDateTime expiry() {
            return expiryTime != null ? LocalDateTime.ofInstant(Instant.parse(expiryTime), ZONE) : null;
        }

        public boolean autoRenewing() {
            return autoRenewingPlan != null && Boolean.TRUE.equals(autoRenewingPlan.autoRenewEnabled());
        }

        public String basePlanId() {
            return offerDetails != null ? offerDetails.basePlanId() : null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AutoRenewingPlan(Boolean autoRenewEnabled) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OfferDetails(String basePlanId, String offerId) {
    }

    // ── API ──

    public SubscriptionPurchaseV2 getSubscription(String purchaseToken) {
        return call(() -> restClient.get()
                .uri("/applications/{pkg}/purchases/subscriptionsv2/tokens/{token}",
                        properties.packageName(), purchaseToken)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(SubscriptionPurchaseV2.class), "구독 조회");
    }

    public void acknowledge(String productId, String purchaseToken) {
        call(() -> restClient.post()
                .uri("/applications/{pkg}/purchases/subscriptions/{productId}/tokens/{token}:acknowledge",
                        properties.packageName(), productId, purchaseToken)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .toBodilessEntity(), "확인 처리");
    }

    private <T> T call(java.util.function.Supplier<T> request, String label) {
        if (!properties.configured()) throw new PlayBillingException(503, "Google Play 결제 설정이 아직 없어요.");
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            log.warn("[PlayBilling] {} 실패 status={}", label, e.getStatusCode().value());
            throw new PlayBillingException(e.getStatusCode().value(), "Google Play 결제를 확인하지 못했어요.");
        } catch (ResourceAccessException e) {
            log.warn("[PlayBilling] {} 응답 없음: {}", label, e.getMessage());
            throw new PlayBillingException(0, "Google Play 와 연결하지 못했어요. 잠시 후 다시 시도해 주세요.");
        }
    }

    private String bearer() {
        try {
            GoogleCredentials c = credentials;
            if (c == null) {
                synchronized (this) {
                    if (credentials == null) {
                        try (InputStream in = new FileInputStream(properties.serviceAccountPath())) {
                            credentials = GoogleCredentials.fromStream(in).createScoped(List.of(SCOPE));
                        }
                    }
                    c = credentials;
                }
            }
            c.refreshIfExpired();
            AccessToken token = c.getAccessToken();
            return "Bearer " + token.getTokenValue();
        } catch (IOException e) {
            log.error("[PlayBilling] 서비스 계정 인증 실패: {}", e.getMessage());
            throw new PlayBillingException(503, "Google Play 결제 설정을 읽지 못했어요.");
        }
    }
}
