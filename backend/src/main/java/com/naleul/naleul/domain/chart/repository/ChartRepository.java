package com.naleul.naleul.domain.chart.repository;

import com.naleul.naleul.domain.chart.repository.projection.CategoryStatProjection;
import com.naleul.naleul.domain.chart.repository.projection.GoalCategoryStatProjection;
import com.naleul.naleul.domain.chart.repository.projection.GoalGeneralStatProjection;
import com.naleul.naleul.domain.actualTask.entity.TaskActual;
import com.naleul.naleul.domain.chart.repository.projection.RoutineTimeProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChartRepository extends JpaRepository<TaskActual, Long> {

    // 7-1-1: goalCategory별 실제 소요 시간 합계 (기간 옵션)
    // ⚠️ DELETED 목표 제외 + "전체 목표" 도넛 필터링용 goalCategoryStatus 추가
    //    (반환 타입이 CategoryStatProjection → GoalCategoryStatProjection으로 바뀜)
    @Query("""
        SELECT ta.goalCategory.goalCategoryId AS categoryId,
               g.goalCategoryName             AS categoryName,
               gc.colorCode                   AS colorHex,
               g.goalCategoryStatus           AS goalCategoryStatus,
               COALESCE(SUM(ta.actualDurationMinutes), 0) AS totalMinutes
        FROM TaskActual ta
        JOIN ta.goalCategory g
        LEFT JOIN g.userColor gc
        WHERE ta.user.userId = :userId
          AND ta.actualEndAt IS NOT NULL
          AND g.goalCategoryStatus <> 'DELETED'
          AND (:startDateTime IS NULL OR ta.actualStartAt >= :startDateTime)
          AND (:endDateTime IS NULL OR ta.actualStartAt < :endDateTime)
        GROUP BY ta.goalCategory.goalCategoryId, g.goalCategoryName, gc.colorCode, g.goalCategoryStatus
    """)
    List<GoalCategoryStatProjection> findGoalCategoryStats(
            @Param("userId") Long userId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    // 7-2-1: goalCategory + generalCategory별 실제 소요 시간 합계 (기간 옵션)
    @Query("""
        SELECT ta.goalCategory.goalCategoryId       AS goalCategoryId,
               g.goalCategoryName                   AS goalCategoryName,
               gc2.colorCode                        AS goalColorHex,
               ta.generalCategory.generalCategoryId AS generalCategoryId,
               ge.generalCategoryName               AS generalCategoryName,
               gc1.colorCode                        AS generalColorHex,
               COALESCE(SUM(ta.actualDurationMinutes), 0) AS totalMinutes
        FROM TaskActual ta
        JOIN ta.goalCategory g
        LEFT JOIN g.userColor gc2
        JOIN ta.generalCategory ge
        LEFT JOIN ge.color gc1
        WHERE ta.user.userId = :userId
          AND ta.actualEndAt IS NOT NULL
          AND g.goalCategoryStatus <> 'DELETED'
          AND (:startDateTime IS NULL OR ta.actualStartAt >= :startDateTime)
          AND (:endDateTime IS NULL OR ta.actualStartAt < :endDateTime)
        GROUP BY ta.goalCategory.goalCategoryId, g.goalCategoryName, gc2.colorCode,
                 ta.generalCategory.generalCategoryId, ge.generalCategoryName, gc1.colorCode
    """)
    List<GoalGeneralStatProjection> findGoalGeneralCategoryStats(
            @Param("userId") Long userId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    // 7-3-1: generalCategory별 실제 소요 시간 합계 (기간 옵션)
    @Query("""
        SELECT ta.generalCategory.generalCategoryId AS categoryId,
               ge.generalCategoryName               AS categoryName,
               gc.colorCode                         AS colorHex,
               COALESCE(SUM(ta.actualDurationMinutes), 0) AS totalMinutes
        FROM TaskActual ta
        JOIN ta.generalCategory ge
        LEFT JOIN ge.color gc
        WHERE ta.user.userId = :userId
          AND ta.actualEndAt IS NOT NULL
          AND ta.goalCategory.goalCategoryStatus <> 'DELETED'
          AND (:startDateTime IS NULL OR ta.actualStartAt >= :startDateTime)
          AND (:endDateTime IS NULL OR ta.actualStartAt < :endDateTime)
        GROUP BY ta.generalCategory.generalCategoryId, ge.generalCategoryName, gc.colorCode
    """)
    List<CategoryStatProjection> findGeneralCategoryStats(
            @Param("userId") Long userId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    // 특정 goalCategory 안에서 generalCategory별 실제 소요 시간 (기간 옵션)
    @Query("""
        SELECT ta.generalCategory.generalCategoryId AS categoryId,
               ge.generalCategoryName               AS categoryName,
               gc.colorCode                         AS colorHex,
               COALESCE(SUM(ta.actualDurationMinutes), 0) AS totalMinutes
        FROM TaskActual ta
        JOIN ta.generalCategory ge
        LEFT JOIN ge.color gc
        WHERE ta.goalCategory.goalCategoryId = :goalCategoryId
          AND ta.actualEndAt IS NOT NULL
          AND ta.goalCategory.goalCategoryStatus <> 'DELETED'
          AND (:startDateTime IS NULL OR ta.actualStartAt >= :startDateTime)
          AND (:endDateTime IS NULL OR ta.actualStartAt < :endDateTime)
        GROUP BY ta.generalCategory.generalCategoryId, ge.generalCategoryName, gc.colorCode
    """)
    List<CategoryStatProjection> findGeneralCategoryStatsByGoalCategory(
            @Param("goalCategoryId") Long goalCategoryId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    // 특정 goalCategory 안에서 routine별 실제 소요 시간 (TaskActual → Task → Routine 조인, 기간 옵션)
    @Query("""
        SELECT r.routineId                         AS routineId,
               r.routineName                       AS routineName,
               r.generalCategory.generalCategoryId  AS generalCategoryId,
               COALESCE(SUM(ta.actualDurationMinutes), 0) AS totalMinutes
        FROM TaskActual ta
        JOIN ta.task t
        JOIN t.routine r
        WHERE r.goalCategory.goalCategoryId = :goalCategoryId
          AND ta.actualEndAt IS NOT NULL
          AND r.goalCategory.goalCategoryStatus <> 'DELETED'
          AND (:startDateTime IS NULL OR ta.actualStartAt >= :startDateTime)
          AND (:endDateTime IS NULL OR ta.actualStartAt < :endDateTime)
        GROUP BY r.routineId, r.routineName, r.generalCategory.generalCategoryId
    """)
    List<RoutineTimeProjection> findRoutineTimeStatsByGoalCategory(
            @Param("goalCategoryId") Long goalCategoryId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );
}