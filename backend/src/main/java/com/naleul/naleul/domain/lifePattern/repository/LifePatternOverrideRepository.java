package com.naleul.naleul.domain.lifePattern.repository;

import com.naleul.naleul.domain.lifePattern.entity.LifePatternOverride;
import com.naleul.naleul.domain.lifePattern.entity.LifePattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LifePatternOverrideRepository extends JpaRepository<LifePatternOverride, Long> {

    /**
     * 조회 기간의 예외. 자정을 넘기는 블록 때문에 호출하는 쪽은 startDate 를 하루 앞당겨 넘긴다.
     */
    @Query("""
        SELECT o FROM LifePatternOverride o
        JOIN FETCH o.lifePattern
        WHERE o.userId = :userId
          AND o.targetDate BETWEEN :startDate AND :endDate
    """)
    List<LifePatternOverride> findAllInRange(@Param("userId") Long userId,
                                             @Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);

    /** from 이후 "시간만 바꾼" 예외를 지운다 (비운 날은 남김) — 기본값을 바꿀 때 새 기본값을 따르게 */
    @Modifying(flushAutomatically = true)
    @Query("""
        DELETE FROM LifePatternOverride o
        WHERE o.lifePattern = :pattern
          AND o.targetDate >= :from
          AND o.skipped = false
    """)
    int deleteMovedFrom(@Param("pattern") LifePattern pattern, @Param("from") LocalDate from);

    @Query("""
        SELECT o FROM LifePatternOverride o
        WHERE o.lifePattern.lifePatternId = :lifePatternId
          AND o.targetDate = :targetDate
    """)
    Optional<LifePatternOverride> findByPatternAndDate(@Param("lifePatternId") Long lifePatternId,
                                                       @Param("targetDate") LocalDate targetDate);
}
