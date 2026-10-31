package com.naleul.naleul.domain.purchase.service;

import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.NotificationTypeV2;
import com.apple.itunes.storekit.model.ResponseBodyV2DecodedPayload;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import com.apple.itunes.storekit.verification.VerificationException;
import com.apple.itunes.storekit.verification.VerificationStatus;
import com.naleul.naleul.domain.purchase.entity.PurchaseTransaction;
import com.naleul.naleul.domain.purchase.enums.RevenueEventType;
import com.naleul.naleul.domain.purchase.enums.SubscriptionStatus;
import com.naleul.naleul.domain.purchase.repository.PurchaseTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class IapNotificationService {

    @Qualifier("signedDataVerifierProduction")
    private final SignedDataVerifier signedDataVerifierProduction;

    @Qualifier("signedDataVerifierSandbox")
    private final SignedDataVerifier signedDataVerifierSandbox;

    private final PurchaseTransactionRepository purchaseTransactionRepository;
    private final IapRevenueRecorder revenueRecorder;

    @Transactional
    public void handle(String signedPayload) {
        ResponseBodyV2DecodedPayload payload;
        try {
            payload = verifyNotification(signedPayload);
        } catch (VerificationException e) {
            log.error("웹훅 알림 검증 실패", e);
            return; // 검증 실패 시 조용히 종료 (Apple에게는 200 응답)
        }

        JWSTransactionDecodedPayload transactionInfo;
        try {
            transactionInfo = verifyTransaction(payload.getData().getSignedTransactionInfo());
        } catch (VerificationException e) {
            log.error("트랜잭션 정보 검증 실패", e);
            return;
        }

        PurchaseTransaction transaction = purchaseTransactionRepository
                .findByOriginalTransactionId(transactionInfo.getOriginalTransactionId())
                .orElse(null);

        if (transaction == null) {
            log.warn("알 수 없는 거래에 대한 알림: {}", transactionInfo.getOriginalTransactionId());
            return;
        }

        applyNotification(transaction, payload.getNotificationType(), transactionInfo);
        recordRevenue(transaction.getUserId(), payload.getNotificationType(), transactionInfo);

        // TODO: userService.updatePremiumStatus(transaction.getUserId(), transaction.getStatus());
    }

    // ⭐ 알림(payload) 자체 검증 — Production 우선, 환경 불일치 시 Sandbox 폴백
    private ResponseBodyV2DecodedPayload verifyNotification(String signedPayload) throws VerificationException {
        try {
            return signedDataVerifierProduction.verifyAndDecodeNotification(signedPayload);
        } catch (VerificationException e) {
            if (e.getStatus() == VerificationStatus.INVALID_ENVIRONMENT) {
                log.info("알림 Production 검증 실패(환경 불일치) → Sandbox로 재시도");
                return signedDataVerifierSandbox.verifyAndDecodeNotification(signedPayload);
            }
            throw e;
        }
    }

    // ⭐ 트랜잭션 정보 검증 — 동일한 패턴
    private JWSTransactionDecodedPayload verifyTransaction(String signedTransactionInfo) throws VerificationException {
        try {
            return signedDataVerifierProduction.verifyAndDecodeTransaction(signedTransactionInfo);
        } catch (VerificationException e) {
            if (e.getStatus() == VerificationStatus.INVALID_ENVIRONMENT) {
                log.info("트랜잭션 Production 검증 실패(환경 불일치) → Sandbox로 재시도");
                return signedDataVerifierSandbox.verifyAndDecodeTransaction(signedTransactionInfo);
            }
            throw e;
        }
    }

    private void applyNotification(PurchaseTransaction transaction, NotificationTypeV2 type,
                                   JWSTransactionDecodedPayload transactionInfo) {
        switch (type) {
            case DID_RENEW -> transaction.updateStatus(
                    SubscriptionStatus.ACTIVE, toLocalDateTime(transactionInfo.getExpiresDate()));
            case EXPIRED -> transaction.updateStatus(SubscriptionStatus.EXPIRED, null);
            case DID_FAIL_TO_RENEW -> transaction.updateStatus(SubscriptionStatus.BILLING_RETRY, null);
            case GRACE_PERIOD_EXPIRED -> transaction.updateStatus(SubscriptionStatus.EXPIRED, null);
            case REFUND, REVOKE -> transaction.updateStatus(SubscriptionStatus.REVOKED, null);
            default -> log.info("별도 처리 없이 로그만 남기는 알림 타입: {}", type);
        }
    }

    /** 매출 대시보드용 결제 기록 — 갱신 결제는 앱이 열리지 않아도 웹훅으로만 들어온다 */
    private void recordRevenue(Long userId, NotificationTypeV2 type, JWSTransactionDecodedPayload transactionInfo) {
        switch (type) {
            case SUBSCRIBED -> revenueRecorder.record(userId, transactionInfo, RevenueEventType.PURCHASE);
            case DID_RENEW -> revenueRecorder.record(userId, transactionInfo, RevenueEventType.RENEWAL);
            case REFUND -> revenueRecorder.record(userId, transactionInfo, RevenueEventType.REFUND);
            default -> {
            }
        }
    }

    private LocalDateTime toLocalDateTime(Long epochMillis) {
        if (epochMillis == null) return null;
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}