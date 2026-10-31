package com.naleul.naleul.domain.playBilling.repository;

import com.naleul.naleul.domain.playBilling.entity.PlaySubscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlaySubscriptionRepository extends JpaRepository<PlaySubscription, Long> {

    /** 같은 토큰을 앱 · 알림이 동시에 처리해도 한 번만 반영되게 행 잠금 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PlaySubscription s WHERE s.purchaseToken = :token")
    Optional<PlaySubscription> findByPurchaseTokenForUpdate(@Param("token") String token);

    List<PlaySubscription> findByUserIdOrderByExpiryTimeDesc(Long userId);
}
