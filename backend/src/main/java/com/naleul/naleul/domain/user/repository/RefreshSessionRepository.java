package com.naleul.naleul.domain.user.repository;

import com.naleul.naleul.domain.user.entity.RefreshSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, Long> {

    /** 같은 토큰으로 동시에 온 재발급이 차례로 처리되도록 잠근다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM RefreshSession s WHERE s.tokenHash = :hash")
    Optional<RefreshSession> findByTokenHashForUpdate(@Param("hash") String hash);

    @Query("SELECT s FROM RefreshSession s WHERE s.previousTokenHash = :hash")
    Optional<RefreshSession> findByPreviousTokenHash(@Param("hash") String hash);

    Optional<RefreshSession> findByTokenHash(String tokenHash);

    @Query("SELECT s FROM RefreshSession s WHERE s.userId = :userId ORDER BY s.lastUsedAt DESC")
    List<RefreshSession> findAllByUserIdRecentFirst(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM RefreshSession s WHERE s.userId = :userId AND s.expiresAt < :now")
    int deleteExpired(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM RefreshSession s WHERE s.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
