package com.naleul.naleul.domain.aiUsage.quota;

import com.naleul.naleul.domain.aiUsage.enums.AiFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AiQuotaUsageRepository extends JpaRepository<AiQuotaUsage, Long> {

    long countByUserIdAndFeatureAndUsedAtGreaterThanEqual(Long userId, AiFeature feature, LocalDateTime from);

    @Modifying
    @Query("DELETE FROM AiQuotaUsage u WHERE u.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
