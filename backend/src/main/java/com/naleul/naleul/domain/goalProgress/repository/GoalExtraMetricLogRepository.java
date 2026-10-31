package com.naleul.naleul.domain.goalProgress.repository;

import com.naleul.naleul.domain.goalProgress.entity.GoalExtraMetricLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GoalExtraMetricLogRepository extends JpaRepository<GoalExtraMetricLog, Long> {

    @Query("""
        SELECT l FROM GoalExtraMetricLog l
        WHERE l.metric.goalMetricId IN :metricIds
        ORDER BY l.logDate ASC
    """)
    List<GoalExtraMetricLog> findAllOf(@Param("metricIds") Collection<Long> metricIds);

    @Query("""
        SELECT l FROM GoalExtraMetricLog l
        WHERE l.metric.goalMetricId = :metricId AND l.logDate = :logDate
    """)
    Optional<GoalExtraMetricLog> findOne(@Param("metricId") Long metricId, @Param("logDate") LocalDate logDate);

    /** 가장 최근 기록 (현재 값 맞추기용) */
    Optional<GoalExtraMetricLog> findFirstByMetric_GoalMetricIdOrderByLogDateDesc(Long metricId);
}
