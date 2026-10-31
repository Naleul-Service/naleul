package com.naleul.naleul.domain.routine.repository;

import com.naleul.naleul.domain.goalCategory.repository.projection.GoalCountProjection;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.enums.RoutineStatus;
import com.naleul.naleul.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoutineRepository extends JpaRepository<Routine, Long> {

    // 특정 GeneralCategory(세부 목표) 하나당 Routine 개수를 셈
    @Query("SELECT COUNT(r) FROM Routine r " +
            "WHERE r.generalCategory.generalCategoryId = :generalCategoryId " +
            "AND r.routineStatus <> com.naleul.naleul.domain.routine.enums.RoutineStatus.DELETED")
    long countActiveByGeneralCategoryId(@Param("generalCategoryId") Long generalCategoryId);

    // 단건 조회 - 유저 소유 확인 + repeatDays 함께 조회
    @Query("""
                SELECT r FROM Routine r
                LEFT JOIN FETCH r.repeatDays
                WHERE r.routineId = :routineId
                  AND r.user = :user
                  AND r.routineStatus != 'DELETED'
            """)
    Optional<Routine> findByRoutineIdAndUser(
            @Param("routineId") Long routineId,
            @Param("user") User user
    );

    // 전체 루틴 목록 조회 - repeatDays 없는 루틴도 포함
    @Query("""
                SELECT DISTINCT r FROM Routine r
                LEFT JOIN FETCH r.repeatDays
                WHERE r.user = :user
                  AND r.routineStatus != 'DELETED'
            """)
    List<Routine> findAllByUser(@Param("user") User user);

    // goalCategoryId로 DELETED 아닌 루틴 조회
    @Query("""
                SELECT DISTINCT r FROM Routine r
                LEFT JOIN FETCH r.repeatDays
                WHERE r.goalCategory.goalCategoryId = :goalCategoryId
                  AND r.routineStatus != 'DELETED'
            """)
    List<Routine> findAllByGoalCategoryId(@Param("goalCategoryId") Long goalCategoryId);

    // 유저의 goalCategory별 활성 루틴 개수를 한번에 GROUP BY로 조회 (N+1 방지)
    @Query("""
                SELECT r.goalCategory.goalCategoryId AS goalCategoryId, COUNT(r) AS count
                FROM Routine r
                WHERE r.user.userId = :userId
                  AND r.routineStatus <> com.naleul.naleul.domain.routine.enums.RoutineStatus.DELETED
                GROUP BY r.goalCategory.goalCategoryId
            """)
    List<GoalCountProjection> countActiveGroupedByGoalCategory(@Param("userId") Long userId);

    @Query("""
                SELECT DISTINCT r FROM Routine r
                LEFT JOIN FETCH r.repeatDays
                LEFT JOIN FETCH r.goalCategory gc
                LEFT JOIN FETCH gc.userColor
                LEFT JOIN FETCH r.generalCategory ge
                LEFT JOIN FETCH ge.color
                WHERE r.user = :user
                  AND r.routineStatus != 'DELETED'
                  AND gc.goalCategoryStatus != 'DELETED'
                  AND ge.generalCategoryStatus != 'DELETED'
                ORDER BY r.repeatStartDate DESC
            """)
    List<Routine> findAllByUserWithDetails(@Param("user") User user);

    // GeneralCategory 하나에 속한 활성(삭제 안 된) Routine 전체 조회
    @Query("""
                SELECT r FROM Routine r
                WHERE r.generalCategory.generalCategoryId = :generalCategoryId
                  AND r.routineStatus <> 'DELETED'
            """)
    List<Routine> findAllActiveByGeneralCategoryId(@Param("generalCategoryId") Long generalCategoryId);

    void deleteAllByUser_UserId(Long userId);

    // ✅ 히트맵용 — routineId 목록으로 Routine + repeatDays를 한번에 조회 (N+1 방지)
    @Query("""
                SELECT DISTINCT r FROM Routine r
                LEFT JOIN FETCH r.repeatDays
                WHERE r.routineId IN :routineIds
            """)
    List<Routine> findAllByRoutineIdInWithRepeatDays(@Param("routineIds") List<Long> routineIds);

    @Query("""
            SELECT r FROM Routine r
            WHERE r.repeatEndDate < :today
              AND r.routineStatus NOT IN (:excludedStatuses)
            """)
    List<Routine> findExpiredRoutines(@Param("today") LocalDate today,
                                      @Param("excludedStatuses") List<RoutineStatus> excludedStatuses);

    @Query("""
            SELECT r FROM Routine r
            WHERE r.repeatStartDate <= :today
              AND r.routineStatus = 'NOT_STARTED'
            """)
    List<Routine> findRoutinesToStart(@Param("today") LocalDate today);
}