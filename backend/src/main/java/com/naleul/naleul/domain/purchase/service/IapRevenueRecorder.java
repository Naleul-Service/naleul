package com.naleul.naleul.domain.purchase.service;

import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.naleul.naleul.domain.purchase.entity.IapRevenueEvent;
import com.naleul.naleul.domain.purchase.enums.RevenueEventType;
import com.naleul.naleul.domain.purchase.repository.IapRevenueEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 결제 금액을 iap_revenue_event 에 남긴다 (매출 대시보드용).
 *
 * 규칙: 기록이 실패해도 결제 처리(프리미엄 부여)는 절대 실패하면 안 된다.
 *  → 별도 트랜잭션(REQUIRES_NEW)을 try 안에서 열고, 모든 예외를 삼킨다. (JpaAiCallRecorder 와 같은 이유)
 */
@Slf4j
@Component
public class IapRevenueRecorder {

    private final IapRevenueEventRepository repository;
    private final TransactionTemplate tx;

    public IapRevenueRecorder(IapRevenueEventRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(Long userId, JWSTransactionDecodedPayload p, RevenueEventType type) {
        try {
            if (p == null || p.getTransactionId() == null) return;
            tx.executeWithoutResult(status -> {
                // 같은 결제가 "앱 확인 요청(RENEWAL)" + "웹훅(SUBSCRIBED → PURCHASE)" 처럼 다른 종류로 두 번 올 수 있다
                //  → 결제(PURCHASE · RENEWAL)끼리는 종류를 가리지 않고 transactionId 하나당 한 번만 센다
                List<RevenueEventType> sameKind = type == RevenueEventType.REFUND
                        ? List.of(RevenueEventType.REFUND)
                        : List.of(RevenueEventType.PURCHASE, RevenueEventType.RENEWAL);
                if (repository.existsByTransactionIdAndEventTypeIn(p.getTransactionId(), sameKind)) return;
                Long when = type == RevenueEventType.REFUND && p.getRevocationDate() != null
                        ? p.getRevocationDate() : p.getPurchaseDate();
                repository.save(IapRevenueEvent.of(
                        userId,
                        p.getTransactionId(),
                        p.getOriginalTransactionId() != null ? p.getOriginalTransactionId() : p.getTransactionId(),
                        p.getProductId(),
                        type,
                        price(p.getPrice()),
                        p.getCurrency(),
                        p.getEnvironment() != null ? p.getEnvironment().toString() : "UNKNOWN",
                        toLocalDateTime(when)));
            });
        } catch (Exception e) {
            log.warn("[IapRevenueRecorder] 기록 실패 transactionId={} type={} msg={}",
                    p != null ? p.getTransactionId() : null, type, e.getMessage());
        }
    }

    /** Apple price 는 밀리 단위 정수 (4,900원 → 4900000) */
    static BigDecimal price(Long milliUnits) {
        if (milliUnits == null) return null;
        return BigDecimal.valueOf(milliUnits).divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
    }

    private static LocalDateTime toLocalDateTime(Long epochMillis) {
        if (epochMillis == null) return LocalDateTime.now();
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
