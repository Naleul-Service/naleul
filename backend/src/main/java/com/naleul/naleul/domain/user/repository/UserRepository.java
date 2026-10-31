package com.naleul.naleul.domain.user.repository;

import com.naleul.naleul.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUserEmail(String userEmail);
    Optional<User> findFirstByUserEmailOrderByUserIdAsc(String userEmail);
    Optional<User> findByAppleId(String appleId);  // 추가
    @Query("""
    SELECT u FROM User u
    WHERE u.isPremium = true
    AND u.premiumExpiresAt IS NOT NULL
    AND u.premiumExpiresAt < :now
    """)
    List<User> findExpiredPremiumUsers(@Param("now") LocalDateTime now);
}