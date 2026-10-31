package com.naleul.naleul.domain.playBilling.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naleul.naleul.domain.playBilling.client.PlayBillingException;
import com.naleul.naleul.domain.playBilling.client.PlayDeveloperClient;
import com.naleul.naleul.domain.playBilling.config.PlayBillingProperties;
import com.naleul.naleul.domain.playBilling.dto.request.PlayVerifyRequest;
import com.naleul.naleul.domain.playBilling.dto.response.PlayBillingStatusResponse;
import com.naleul.naleul.domain.playBilling.entity.PlaySubscription;
import com.naleul.naleul.domain.playBilling.repository.PlaySubscriptionRepository;
import com.naleul.naleul.domain.playBilling.support.PlayAccountId;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.domain.webBilling.repository.WebSubscriptionRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Android Google Play 구독.
 *
 * 흐름: 앱이 Play 결제 → purchaseToken 을 verify 로 보냄 → 서버가 Play 에 상태 조회 ·
 *       결제한 계정 확인(obfuscatedAccountId) → Pro 기간 연장 → 확인 처리(acknowledge, 3일 안에 안 하면 자동 환불)
 * 갱신 · 해지 · 환불: Play 실시간 알림(RTDN) → 같은 토큰을 다시 조회해 맞춘다.
 *  - 갱신되면 Play 의 새 만료 시각까지 Pro 를 늘린다
 *  - 해지(자동 갱신 끔)는 기간 끝까지 Pro, 그 뒤엔 자정 만료 배치가 끈다
 *  - 환불(REVOKED)은 바로 Pro 를 끈다 (다른 결제로 더 늦게까지 Pro 면 그대로)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayBillingService {

    /** RTDN notificationType 12 = SUBSCRIPTION_REVOKED (환불 등으로 바로 회수) */
    private static final int NOTIFICATION_REVOKED = 12;

    private final PlaySubscriptionRepository repository;
    private final PlayDeveloperClient client;
    private final PlayBillingProperties properties;
    private final UserFinder userFinder;
    private final WebSubscriptionRepository webSubscriptionRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PlayBillingStatusResponse status(Long userId) {
        User user = userFinder.findUser(userId);
        LocalDateTime now = LocalDateTime.now();
        PlaySubscription current = repository.findByUserIdOrderByExpiryTimeDesc(userId).stream()
                .filter(s -> s.entitlesNow(now))
                .findFirst().orElse(null);
        return new PlayBillingStatusResponse(properties.configured(), user.isCurrentlyPremium(),
                user.isCurrentlyPremium() ? user.getPremiumExpiresAt() : null,
                current != null ? PlayBillingStatusResponse.Subscription.of(current) : null);
    }

    /** 앱이 결제 직후 · 구입 내역 복원 · 멤버십 화면 진입 때 보내요. 같은 토큰을 여러 번 보내도 결과는 같아요 */
    @Transactional
    public PlayBillingStatusResponse verify(Long userId, PlayVerifyRequest request) {
        if (!properties.configured()) throw new CustomException(ErrorCode.PLAY_BILLING_NOT_CONFIGURED);
        PlayDeveloperClient.SubscriptionPurchaseV2 purchase = fetch(request.purchaseToken());

        // 이 나를 계정으로 결제한 구독인지 (남의 구매 토큰으로 Pro 를 받지 못하게)
        String expected = PlayAccountId.of(userId);
        if (purchase.obfuscatedAccountId() != null && !expected.equals(purchase.obfuscatedAccountId())) {
            throw new CustomException(ErrorCode.PLAY_BILLING_OTHER_ACCOUNT);
        }

        PlaySubscription sub = repository.findByPurchaseTokenForUpdate(request.purchaseToken()).orElse(null);
        if (sub != null && !sub.getUserId().equals(userId)) throw new CustomException(ErrorCode.PLAY_BILLING_OTHER_ACCOUNT);
        if (sub == null) sub = PlaySubscription.of(userId, request.purchaseToken());
        sub.apply(purchase, request.productId());
        repository.save(sub);
        supersedeLinked(sub);

        User user = userFinder.findUser(userId);
        LocalDateTime now = LocalDateTime.now();
        if (sub.entitlesNow(now)) {
            user.extendPremium(sub.getExpiryTime());
            acknowledgeIfNeeded(sub);
            log.info("[PlayBilling] 구독 확인 userId={} product={} state={} until={} test={}", userId,
                    sub.getProductId(), sub.getState(), sub.getExpiryTime(), sub.isTestPurchase());
        } else {
            log.info("[PlayBilling] Pro 아님 userId={} state={}", userId, sub.getState());
        }
        return status(userId);
    }

    /**
     * Play 실시간 개발자 알림 (Pub/Sub push). body.message.data = base64(JSON)
     * 서버가 모르는 토큰(앱이 아직 verify 를 못 보낸 구매)은 건너뛰어요 — 앱이 다음에 verify 할 때 맞춰져요.
     */
    @Transactional
    public void handleRtdn(String pushToken, String body) {
        if (!properties.rtdnToken().isBlank() && !properties.rtdnToken().equals(pushToken)) {
            log.warn("[PlayBilling] RTDN 토큰 불일치 — 무시");
            return;
        }
        String purchaseToken;
        int type;
        try {
            JsonNode root = objectMapper.readTree(body);
            String data = root.path("message").path("data").asText("");
            if (data.isBlank()) return;
            JsonNode n = objectMapper.readTree(new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8));
            if (n.has("testNotification")) {
                log.info("[PlayBilling] RTDN 테스트 알림 받음");
                return;
            }
            JsonNode s = n.path("subscriptionNotification");
            if (s.isMissingNode()) return;
            purchaseToken = s.path("purchaseToken").asText(null);
            type = s.path("notificationType").asInt(0);
        } catch (Exception e) {
            log.warn("[PlayBilling] RTDN 본문을 읽지 못함: {}", e.getMessage());
            return;
        }
        if (purchaseToken == null) return;

        PlaySubscription sub = repository.findByPurchaseTokenForUpdate(purchaseToken).orElse(null);
        if (sub == null) {
            log.info("[PlayBilling] RTDN 모르는 토큰 type={} — 앱의 verify 를 기다림", type);
            return;
        }
        PlayDeveloperClient.SubscriptionPurchaseV2 purchase;
        try {
            purchase = client.getSubscription(purchaseToken);
        } catch (PlayBillingException e) {
            log.warn("[PlayBilling] RTDN 조회 실패 type={} status={}", type, e.getStatus());
            if (e.notFound()) return;
            throw e;   // 500 → Pub/Sub 이 다시 보내요
        }
        LocalDateTime before = sub.getExpiryTime();
        sub.apply(purchase, sub.getProductId());
        supersedeLinked(sub);

        User user = userFinder.findUser(sub.getUserId());
        LocalDateTime now = LocalDateTime.now();
        if (sub.entitlesNow(now)) {
            user.extendPremium(sub.getExpiryTime());
            acknowledgeIfNeeded(sub);
        } else if (type == NOTIFICATION_REVOKED) {
            revokeIfPlayWasLatest(user, before, now);
        }
        log.info("[PlayBilling] RTDN type={} userId={} state={} until={}", type, sub.getUserId(), sub.getState(),
                sub.getExpiryTime());
    }

    private PlayDeveloperClient.SubscriptionPurchaseV2 fetch(String token) {
        try {
            return client.getSubscription(token);
        } catch (PlayBillingException e) {
            if (e.notFound()) throw new CustomException(ErrorCode.PLAY_BILLING_INVALID_PURCHASE);
            if (e.getStatus() == 503) throw new CustomException(ErrorCode.PLAY_BILLING_NOT_CONFIGURED);
            throw new CustomException(ErrorCode.PLAY_BILLING_VERIFY_FAILED);
        }
    }

    /** 요금제를 바꾸면 새 토큰이 생겨요 — 이전 토큰은 더 이상 Pro 근거가 아님 */
    private void supersedeLinked(PlaySubscription sub) {
        if (sub.getLinkedPurchaseToken() == null) return;
        repository.findByPurchaseTokenForUpdate(sub.getLinkedPurchaseToken()).ifPresent(PlaySubscription::supersede);
    }

    /** 확인 처리 — 실패해도 Pro 는 그대로 두고, 다음 verify · 알림 때 다시 해요 (3일 안) */
    private void acknowledgeIfNeeded(PlaySubscription sub) {
        if (sub.isAcknowledged()) return;
        try {
            client.acknowledge(sub.getProductId(), sub.getPurchaseToken());
            sub.markAcknowledged();
        } catch (PlayBillingException e) {
            log.warn("[PlayBilling] 확인 처리 실패 subscriptionId={} status={}", sub.getPlaySubscriptionId(), e.getStatus());
        }
    }

    /** 환불 — Play 가 Pro 의 마지막 근거였을 때만 끈다 (웹 구독 중이거나 다른 결제로 더 늦게까지 Pro 면 그대로) */
    private void revokeIfPlayWasLatest(User user, LocalDateTime playExpiryBefore, LocalDateTime now) {
        if (!user.hasPaidPremium() || playExpiryBefore == null) return;   // 환불은 실제 결제 기준
        if (user.getPremiumExpiresAt() == null || user.getPremiumExpiresAt().isAfter(playExpiryBefore)) return;
        boolean webCovers = webSubscriptionRepository.findByUserId(user.getUserId())
                .map(w -> w.coversNow(now)).orElse(false);
        if (webCovers) return;
        boolean otherPlay = repository.findByUserIdOrderByExpiryTimeDesc(user.getUserId()).stream()
                .anyMatch(s -> s.entitlesNow(now));
        if (otherPlay) return;
        user.revokePremium();
        log.info("[PlayBilling] 환불로 Pro 회수 userId={}", user.getUserId());
    }
}
