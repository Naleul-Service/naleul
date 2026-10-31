package com.naleul.naleul.domain.webBilling.repository;

import com.naleul.naleul.domain.webBilling.entity.WebSubscription;
import com.naleul.naleul.domain.webBilling.enums.WebSubscriptionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WebSubscriptionRepository extends JpaRepository<WebSubscription, Long> {

    Optional<WebSubscription> findByUserId(Long userId);

    /** 토스페이 콜백(빌링키 삭제) — 빌링키로 구독 찾기 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM WebSubscription s WHERE s.billingKey = :billingKey")
    Optional<WebSubscription> findByBillingKeyForUpdate(@Param("billingKey") String billingKey);

    /** 결제 · 상태 변경 전 행 잠금 — 배포 중 서버 두 대가 같은 구독을 동시에 결제하지 않게 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM WebSubscription s WHERE s.webSubscriptionId = :id")
    Optional<WebSubscription> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM WebSubscription s WHERE s.userId = :userId")
    Optional<WebSubscription> findByUserIdForUpdate(@Param("userId") Long userId);

    /** 자동 결제할 차례인 구독 */
    @Query("""
        SELECT s.webSubscriptionId FROM WebSubscription s
        WHERE s.status IN :statuses AND s.nextChargeAt IS NOT NULL AND s.nextChargeAt <= :now
    """)
    List<Long> findDueIds(@Param("statuses") Collection<WebSubscriptionStatus> statuses,
                          @Param("now") LocalDateTime now);

    /** 해지 신청 후 기간이 끝난 구독 */
    @Query("""
        SELECT s.webSubscriptionId FROM WebSubscription s
        WHERE s.status = com.naleul.naleul.domain.webBilling.enums.WebSubscriptionStatus.CANCELED
          AND s.currentPeriodEnd <= :now
    """)
    List<Long> findEndedCanceledIds(@Param("now") LocalDateTime now);
}
