package com.naleul.naleul.domain.planner.repository;

import com.naleul.naleul.domain.task.entity.Task;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 배치 엔진 전용 조회 (명세 3 · 6장).
 * 기존 TaskRepository(800줄+)를 덮어쓰지 않으려고 같은 Task 엔티티에 대한 별도 Repository 로 분리했다.
 * (Spring Data 는 한 엔티티에 Repository 를 여러 개 둘 수 있다)
 */
public interface PlacementQueryRepository extends Repository<Task, Long> {

    /**
     * 배치에 필요한 그날의 Task 전부.
     *  - 시간 있는 Task: [rangeStart, rangeEnd) 와 겹치는 것 (자정 넘김·전날 늦은 Task 포함)
     *  - 시간 없는 Task: routineDate = date
     * 삭제된 Task 는 제외.
     */
    @Query("""
        SELECT t FROM Task t
        LEFT JOIN FETCH t.goalCategory
        LEFT JOIN FETCH t.routine
        LEFT JOIN FETCH t.milestone
        WHERE t.user.userId = :userId
          AND t.taskStatus <> com.naleul.naleul.domain.task.enums.TaskStatus.DELETED
          AND (
                (t.plannedStartAt IS NOT NULL AND t.plannedStartAt < :rangeEnd
                    AND ( (t.plannedEndAt IS NOT NULL AND t.plannedEndAt > :rangeStart)
                       OR (t.plannedEndAt IS NULL AND t.plannedStartAt >= :rangeStart) ))
             OR (t.plannedStartAt IS NULL AND t.routineDate = :date)
          )
    """)
    List<Task> findForPlacement(@Param("userId") Long userId,
                                @Param("date") LocalDate date,
                                @Param("rangeStart") LocalDateTime rangeStart,
                                @Param("rangeEnd") LocalDateTime rangeEnd);

    /** 단일 Task (다른 날로 넘길 때) */
    @Query("""
        SELECT t FROM Task t
        LEFT JOIN FETCH t.goalCategory
        LEFT JOIN FETCH t.routine
        LEFT JOIN FETCH t.milestone
        WHERE t.taskId = :taskId AND t.user.userId = :userId
    """)
    List<Task> findOneForPlacement(@Param("userId") Long userId, @Param("taskId") Long taskId);

    /**
     * 시간대별 효율 계산 재료: [from, to) 사이에 계획됐던 Task 의 (계획 시작 시각, 상태).
     * 미션 Task 는 방장이 정한 시간이라 사용자의 집중 패턴과 무관해 제외.
     */
    @Query("""
        SELECT t.plannedStartAt, t.taskStatus FROM Task t
        WHERE t.user.userId = :userId
          AND t.plannedStartAt >= :from AND t.plannedStartAt < :to
          AND t.taskStatus <> com.naleul.naleul.domain.task.enums.TaskStatus.DELETED
          AND t.missionParticipant IS NULL
    """)
    List<Object[]> findEfficiencySamples(@Param("userId") Long userId,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);

    /** 루틴별 최근 실제 소요 시간(분) — 계획보다 오래 걸리는 루틴은 늘려서 배치 (DURATION_ADJUSTED) */
    @Query("""
        SELECT t.routine.routineId, ta.actualDurationMinutes FROM Task t
        JOIN t.taskActuals ta
        WHERE t.routine.routineId IN :routineIds
          AND ta.actualStartAt >= :from
          AND ta.actualDurationMinutes IS NOT NULL
          AND ta.actualDurationMinutes > 0
    """)
    List<Object[]> findRoutineActualMinutes(@Param("routineIds") Collection<Long> routineIds,
                                            @Param("from") LocalDateTime from);

    /** 목표 생성 인터뷰에서 받은 선호 시간대 (goal_creation_session.slots JSON) */
    @Query("""
        SELECT s.confirmedGoalCategory.goalCategoryId, s.slots FROM GoalCreationSession s
        WHERE s.confirmedGoalCategory.goalCategoryId IN :goalIds
    """)
    List<Object[]> findGoalSlotsJson(@Param("goalIds") Collection<Long> goalIds);
}
