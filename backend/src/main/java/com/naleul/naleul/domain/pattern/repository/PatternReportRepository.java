package com.naleul.naleul.domain.pattern.repository;

import com.naleul.naleul.domain.pattern.entity.PatternReport;
import com.naleul.naleul.domain.pattern.enums.PatternPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface PatternReportRepository extends JpaRepository<PatternReport, Long> {

    @Query("""
        SELECT r FROM PatternReport r
        WHERE r.userId = :userId AND r.period = :period AND r.baseDate = :baseDate
    """)
    Optional<PatternReport> findCached(@Param("userId") Long userId,
                                       @Param("period") PatternPeriod period,
                                       @Param("baseDate") LocalDate baseDate);

    /** 제안 반영 후 그 사용자의 캐시 전부 삭제 (다음 조회 때 다시 계산) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PatternReport r WHERE r.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    /** 오래된 캐시 정리 (매일 04:00, base_date < 오늘 − 7일) */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM PatternReport r WHERE r.baseDate < :before")
    int deleteOlderThan(@Param("before") LocalDate before);
}
