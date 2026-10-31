package com.naleul.naleul.domain.purchase.service;

import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.Type;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import com.apple.itunes.storekit.verification.VerificationException;
import com.apple.itunes.storekit.verification.VerificationStatus;
import com.naleul.naleul.domain.purchase.entity.PurchaseTransaction;
import com.naleul.naleul.domain.purchase.enums.ProductType;
import com.naleul.naleul.domain.purchase.enums.RevenueEventType;
import com.naleul.naleul.domain.purchase.enums.SubscriptionStatus;
import com.naleul.naleul.domain.purchase.repository.PurchaseTransactionRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
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
public class IapService {

    @Qualifier("signedDataVerifierProduction")
    private final SignedDataVerifier signedDataVerifierProduction;

    @Qualifier("signedDataVerifierSandbox")
    private final SignedDataVerifier signedDataVerifierSandbox;

    private final PurchaseTransactionRepository purchaseTransactionRepository;
    private final UserFinder userFinder;
    private final IapRevenueRecorder revenueRecorder;

    @Transactional
    public void verifyAndSavePurchase(Long userId, String signedTransaction) {
        JWSTransactionDecodedPayload payload = verifyTransaction(signedTransaction);

        if (isDuplicateTransaction(payload.getTransactionId())) {
            log.info("이미 처리된 거래입니다. transactionId: {}", payload.getTransactionId());
            return;
        }

        purchaseTransactionRepository.findByOriginalTransactionId(payload.getOriginalTransactionId())
                .ifPresentOrElse(
                        existing -> {
                            updateExistingTransaction(existing, payload);
                            revenueRecorder.record(userId, payload, RevenueEventType.RENEWAL);
                        },
                        () -> {
                            createNewTransaction(userId, payload);
                            revenueRecorder.record(userId, payload, RevenueEventType.PURCHASE);
                        }
                );

        grantPremiumToUser(userId, toLocalDateTime(payload.getExpiresDate()));

        log.info("결제 검증 및 프리미엄 부여 완료. userId: {}, productId: {}, environment: {}",
                userId, payload.getProductId(), payload.getEnvironment());
    }

    // ⭐ Production 우선 시도 → 환경 불일치면 Sandbox로 폴백
    private JWSTransactionDecodedPayload verifyTransaction(String signedTransaction) {
        try {
            return signedDataVerifierProduction.verifyAndDecodeTransaction(signedTransaction);
        } catch (VerificationException e) {
            if (e.getStatus() == VerificationStatus.INVALID_ENVIRONMENT) {
                log.info("Production 검증 실패(환경 불일치) → Sandbox로 재시도합니다.");
                try {
                    return signedDataVerifierSandbox.verifyAndDecodeTransaction(signedTransaction);
                } catch (VerificationException sandboxException) {
                    log.error("Sandbox 영수증 검증도 실패: {}", sandboxException.getMessage());
                    throw new CustomException(ErrorCode.INVALID_PURCHASE_RECEIPT);
                }
            }
            log.error("영수증 검증 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.INVALID_PURCHASE_RECEIPT);
        }
    }

    private void createNewTransaction(Long userId, JWSTransactionDecodedPayload payload) {
        purchaseTransactionRepository.save(buildTransaction(userId, payload));
    }

    private void updateExistingTransaction(PurchaseTransaction existing, JWSTransactionDecodedPayload payload) {
        existing.renewFrom(
                payload.getTransactionId(),
                toLocalDateTime(payload.getPurchaseDate()),
                payload.getExpiresDate() != null ? toLocalDateTime(payload.getExpiresDate()) : null,
                SubscriptionStatus.ACTIVE
        );
    }

    private boolean isDuplicateTransaction(String transactionId) {
        return purchaseTransactionRepository.existsByTransactionId(transactionId);
    }

    private PurchaseTransaction buildTransaction(Long userId, JWSTransactionDecodedPayload payload) {
        return PurchaseTransaction.builder()
                .userId(userId)
                .originalTransactionId(payload.getOriginalTransactionId())
                .transactionId(payload.getTransactionId())
                .productId(payload.getProductId())
                .productType(mapProductType(payload.getType()))
                .purchaseDate(toLocalDateTime(payload.getPurchaseDate()))
                .expiresDate(payload.getExpiresDate() != null
                        ? toLocalDateTime(payload.getExpiresDate())
                        : null)
                .status(SubscriptionStatus.ACTIVE)
                .environment(payload.getEnvironment().toString())
                .build();
    }

    private void grantPremiumToUser(Long userId, LocalDateTime expiresAt) {
        User user = userFinder.findUser(userId);
        user.extendPremium(expiresAt);   // 웹 구독으로 더 늦게까지 Pro 면 줄이지 않음
    }

    private ProductType mapProductType(Type type) {
        return switch (type) {
            case AUTO_RENEWABLE_SUBSCRIPTION -> ProductType.AUTO_RENEWABLE_SUBSCRIPTION;
            case NON_CONSUMABLE -> ProductType.NON_CONSUMABLE;
            case CONSUMABLE -> ProductType.CONSUMABLE;
            case NON_RENEWING_SUBSCRIPTION -> ProductType.NON_RENEWING_SUBSCRIPTION;
        };
    }

    private LocalDateTime toLocalDateTime(Long epochMillis) {
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}