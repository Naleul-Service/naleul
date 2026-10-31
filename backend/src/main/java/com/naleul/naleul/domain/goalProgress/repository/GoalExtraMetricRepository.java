package com.naleul.naleul.domain.goalProgress.repository;

import com.naleul.naleul.domain.goalProgress.entity.GoalExtraMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GoalExtraMetricRepository extends JpaRepository<GoalExtraMetric, Long> {

    @Query("""
        SELECT m FROM GoalExtraMetric m
        WHERE m.goalCategory.goalCategoryId = :goalCategoryId AND m.isDeleted = false
        ORDER BY m.sortOrder ASC, m.goalMetricId ASC
    """)
    List<GoalExtraMetric> findAllOfGoal(@Param("goalCategoryId") Long goalCategoryId);

    @Query("""
        SELECT m FROM GoalExtraMetric m
        WHERE m.goalMetricId = :metricId
          AND m.goalCategory.goalCategoryId = :goalCategoryId
          AND m.isDeleted = false
    """)
    Optional<GoalExtraMetric> findInGoal(@Param("metricId") Long metricId, @Param("goalCategoryId") Long goalCategoryId);
}
