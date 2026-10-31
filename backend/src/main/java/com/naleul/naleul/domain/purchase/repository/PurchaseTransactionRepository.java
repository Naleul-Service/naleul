package com.naleul.naleul.domain.purchase.repository;

import com.naleul.naleul.domain.purchase.entity.PurchaseTransaction;
import com.naleul.naleul.domain.purchase.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseTransactionRepository extends JpaRepository<PurchaseTransaction, Long> {

    /**
     * IapService에서 중복 결제 처리 방지용으로 사용
     * "이 transactionId로 이미 저장된 거래가 있는가?"
     */
    boolean existsByTransactionId(String transactionId);

    /**
     * IapNotificationService에서 웹훅 처리 시 사용
     * 구독 갱신/취소 등의 알림이 오면, originalTransactionId(최초 거래 ID)로
     * 기존에 저장해둔 거래를 찾아서 상태를 업데이트함
     */
    Optional<PurchaseTransaction> findByOriginalTransactionId(String originalTransactionId);

    /**
     * transactionId로 단건 조회 (필요 시 사용)
     */
    Optional<PurchaseTransaction> findByTransactionId(String transactionId);

    /**
     * 특정 유저의 모든 구매 내역 조회
     * (마이페이지 - 구매 내역 화면 등에서 사용 가능)
     */
    List<PurchaseTransaction> findByUserIdOrderByPurchaseDateDesc(Long userId);

    /**
     * 특정 유저의 현재 활성화된 구독/구매 내역만 조회
     * (프리미엄 여부 체크할 때 사용)
     */
    List<PurchaseTransaction> findByUserIdAndStatus(Long userId, SubscriptionStatus status);

    /**
     * 특정 유저가 특정 상품에 대해 활성 구독을 가지고 있는지 확인
     * (예: "이 유저가 지금 프리미엄 구독 중인가?")
     */
    boolean existsByUserIdAndProductIdAndStatus(Long userId, String productId, SubscriptionStatus status);
}