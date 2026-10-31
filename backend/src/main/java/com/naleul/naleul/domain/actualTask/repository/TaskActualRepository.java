package com.naleul.naleul.domain.actualTask.repository;

import com.naleul.naleul.domain.actualTask.entity.TaskActual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TaskActualRepository extends JpaRepository<TaskActual, Long> {

    @Query("""
    SELECT COUNT(ta) FROM TaskActual ta
    WHERE ta.user.userId = :userId
      AND ta.task IS NULL
      AND ta.actualStartAt >= :dayStart
      AND ta.actualStartAt < :dayEnd
    """)
    long countIndependentActualsByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd
    );

    Optional<TaskActual> findByTaskTaskId(Long taskId);

    Optional<TaskActual> findFirstByTaskTaskId(Long taskId);

    /** 한 Task 의 실제 기록 전부 (여러 번 나눠서 한 경우 여러 행) */
    List<TaskActual> findAllByTaskTaskIdOrderByActualStartAtAsc(Long taskId);

    @Query("""
    SELECT ta FROM TaskActual ta
    JOIN FETCH ta.goalCategory g
    JOIN FETCH ta.generalCategory ge
    LEFT JOIN FETCH ta.task t
    WHERE ta.user.userId = :userId
      AND ta.actualStartAt < :kstDayEnd
      AND ta.actualEndAt   > :kstDayStart
      AND (:goalCategoryId IS NULL OR ta.goalCategory.goalCategoryId = :goalCategoryId)
      AND (:generalCategoryId IS NULL OR ta.generalCategory.generalCategoryId = :generalCategoryId)
      AND (:sourceType IS NULL
          OR (:sourceType = 'ROUTINE' AND t.routine IS NOT NULL)
          OR (:sourceType = 'MISSION' AND t.missionParticipant IS NOT NULL)
          OR (:sourceType = 'MANUAL' AND (t IS NULL OR (t.routine IS NULL AND t.missionParticipant IS NULL)))
      )
    ORDER BY ta.actualStartAt ASC
""")
    List<TaskActual> findDailyActuals(
            @Param("userId") Long userId,
            @Param("kstDayStart") LocalDateTime kstDayStart,
            @Param("kstDayEnd") LocalDateTime kstDayEnd,
            @Param("goalCategoryId") Long goalCategoryId,
            @Param("generalCategoryId") Long generalCategoryId,
            @Param("sourceType") String sourceType
    );

    @Query("""
SELECT ta FROM TaskActual ta
LEFT JOIN FETCH ta.user
WHERE ta.taskActualId = :taskActualId
  AND ta.user.userId = :userId
""")
    Optional<TaskActual> findByTaskActualIdAndUserId(
            @Param("taskActualId") Long taskActualId,
            @Param("userId") Long userId
    );

    @Query("""
    SELECT ta FROM TaskActual ta
    JOIN FETCH ta.goalCategory g
    JOIN FETCH ta.generalCategory ge
    LEFT JOIN FETCH ta.task t
    WHERE ta.user.userId = :userId
      AND ta.actualStartAt < :kstWeekEnd
      AND ta.actualEndAt   > :kstWeekStart
      AND (:goalCategoryId IS NULL OR ta.goalCategory.goalCategoryId = :goalCategoryId)
      AND (:generalCategoryId IS NULL OR ta.generalCategory.generalCategoryId = :generalCategoryId)
      AND (:sourceType IS NULL
          OR (:sourceType = 'ROUTINE' AND t.routine IS NOT NULL)
          OR (:sourceType = 'MISSION' AND t.missionParticipant IS NOT NULL)
          OR (:sourceType = 'MANUAL' AND (t IS NULL OR (t.routine IS NULL AND t.missionParticipant IS NULL)))
      )
    ORDER BY ta.actualStartAt ASC
    """)
    List<TaskActual> findWeeklyActuals(
            @Param("userId") Long userId,
            @Param("kstWeekStart") LocalDateTime kstWeekStart,
            @Param("kstWeekEnd") LocalDateTime kstWeekEnd,
            @Param("goalCategoryId") Long goalCategoryId,
            @Param("generalCategoryId") Long generalCategoryId,
            @Param("sourceType") String sourceType   // ✅ 추가
    );
}