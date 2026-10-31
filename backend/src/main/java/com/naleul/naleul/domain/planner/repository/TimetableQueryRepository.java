package com.naleul.naleul.domain.planner.repository;

import com.naleul.naleul.domain.milestone.entity.Milestone;
import com.naleul.naleul.domain.task.entity.Task;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * TimeTable(일간·주간·월간) 조회 전용 (명세 3 · 5-2).
 * 기존 TaskRepository 를 덮어쓰지 않으려고 분리했다.
 * 색상·목표·마일스톤을 한 번에 가져와 N+1 을 막는다.
 */
public interface TimetableQueryRepository extends Repository<Task, Long> {

    /** [from, to) 와 겹치는 시간 있는 Task (삭제 제외) */
    @Query("""
        SELECT t FROM Task t
        LEFT JOIN FETCH t.goalCategory g
        LEFT JOIN FETCH g.userColor
        LEFT JOIN FETCH t.generalCategory
        LEFT JOIN FETCH t.routine
        LEFT JOIN FETCH t.milestone
        WHERE t.user.userId = :userId
          AND t.taskStatus <> com.naleul.naleul.domain.task.enums.TaskStatus.DELETED
          AND t.plannedStartAt IS NOT NULL
          AND t.plannedStartAt < :to
          AND ( (t.plannedEndAt IS NOT NULL AND t.plannedEndAt > :from)
             OR (t.plannedEndAt IS NULL AND t.plannedStartAt >= :from) )
        ORDER BY t.plannedStartAt ASC
    """)
    List<Task> findPlacedInRange(@Param("userId") Long userId,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);

    /**
     * 실제 수행 시간이 [from, to) 와 겹치는 완료 Task.
     * 계획한 날과 다른 날(시간)에 완료한 Task 를 "실제로 한 날" 화면에도 보여주려고 따로 가져온다.
     *  - 일반·루틴 Task: task_actual 테이블 ("계획과 다른 시간에 했어요")
     *  - 미션 Task: task.actual_start_at / actual_end_at
     */
    @Query("""
        SELECT DISTINCT t FROM Task t
        LEFT JOIN FETCH t.goalCategory g
        LEFT JOIN FETCH g.userColor
        LEFT JOIN FETCH t.generalCategory
        LEFT JOIN FETCH t.routine
        LEFT JOIN FETCH t.milestone
        WHERE t.user.userId = :userId
          AND t.taskStatus = com.naleul.naleul.domain.task.enums.TaskStatus.COMPLETED
          AND ( (t.actualStartAt IS NOT NULL AND t.actualEndAt IS NOT NULL
                 AND t.actualStartAt < :to AND t.actualEndAt > :from)
             OR EXISTS (SELECT ta.taskActualId FROM TaskActual ta
                        WHERE ta.task = t
                          AND ta.actualStartAt IS NOT NULL AND ta.actualEndAt IS NOT NULL
                          AND ta.actualStartAt < :to AND ta.actualEndAt > :from) )
    """)
    List<Task> findCompletedWithActualInRange(@Param("userId") Long userId,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    /** Task 별 실제 수행 구간: [taskId, 첫 시작, 마지막 종료] (task_actual 이 여러 행이면 전체를 감싸는 구간) */
    @Query("""
        SELECT ta.task.taskId, MIN(ta.actualStartAt), MAX(ta.actualEndAt)
        FROM TaskActual ta
        WHERE ta.task.taskId IN :taskIds
          AND ta.actualStartAt IS NOT NULL
          AND ta.actualEndAt IS NOT NULL
        GROUP BY ta.task.taskId
    """)
    List<Object[]> findActualRanges(@Param("taskIds") Collection<Long> taskIds);


    /** 목표에 속한 모든 Task (AI 생성·직접 추가·루틴 모두, 삭제 제외) — 목표 상세 "관련 Task" */
    @Query("""
        SELECT t FROM Task t
        LEFT JOIN FETCH t.goalCategory g
        LEFT JOIN FETCH g.userColor
        LEFT JOIN FETCH t.generalCategory
        LEFT JOIN FETCH t.routine
        LEFT JOIN FETCH t.milestone
        WHERE t.user.userId = :userId
          AND t.goalCategory.goalCategoryId = :goalCategoryId
          AND t.taskStatus <> com.naleul.naleul.domain.task.enums.TaskStatus.DELETED
    """)
    List<Task> findAllOfGoal(@Param("userId") Long userId,
                             @Param("goalCategoryId") Long goalCategoryId);

    /** 같은 루틴의 [from, to] 날짜 미완료 Task — 루틴 시간을 "이번 주 / 앞으로" 한꺼번에 옮길 때 */
    @Query("""
        SELECT t FROM Task t
        LEFT JOIN FETCH t.goalCategory
        LEFT JOIN FETCH t.routine
        LEFT JOIN FETCH t.milestone
        WHERE t.user.userId = :userId
          AND t.routine.routineId = :routineId
          AND t.taskStatus = com.naleul.naleul.domain.task.enums.TaskStatus.TODO
          AND t.routineDate BETWEEN :from AND :to
        ORDER BY t.routineDate ASC
    """)
    List<Task> findRoutineTodoBetween(@Param("userId") Long userId,
                                      @Param("routineId") Long routineId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    /** 날짜만 있고 시간 없는 Task ("시간 미정") */
    @Query("""
        SELECT t FROM Task t
        LEFT JOIN FETCH t.goalCategory g
        LEFT JOIN FETCH g.userColor
        LEFT JOIN FETCH t.generalCategory
        LEFT JOIN FETCH t.routine
        LEFT JOIN FETCH t.milestone
        WHERE t.user.userId = :userId
          AND t.taskStatus <> com.naleul.naleul.domain.task.enums.TaskStatus.DELETED
          AND t.plannedStartAt IS NULL
          AND t.routineDate BETWEEN :startDate AND :endDate
        ORDER BY t.routineDate ASC, t.taskId ASC
    """)
    List<Task> findUnscheduledInRange(@Param("userId") Long userId,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate);

    /** 기간 안에 마감인 미완료 일회성 Task (월간 "이번 주 마감") */
    @Query("""
        SELECT t FROM Task t
        LEFT JOIN FETCH t.goalCategory g
        LEFT JOIN FETCH g.userColor
        WHERE t.user.userId = :userId
          AND t.taskStatus = com.naleul.naleul.domain.task.enums.TaskStatus.TODO
          AND t.routine IS NULL
          AND t.dueDate BETWEEN :startDate AND :endDate
        ORDER BY t.dueDate ASC, t.taskId ASC
    """)
    List<Task> findDueInRange(@Param("userId") Long userId,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate);

    /** 기간 안에 마감인 마일스톤 (삭제된 목표 제외) — 월간 🏁 표시 */
    @Query("""
        SELECT m FROM Milestone m
        JOIN FETCH m.goalCategory g
        LEFT JOIN FETCH g.userColor
        WHERE g.user.userId = :userId
          AND g.goalCategoryStatus <> com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus.DELETED
          AND m.dueDate BETWEEN :startDate AND :endDate
        ORDER BY m.dueDate ASC, m.sortOrder ASC
    """)
    List<Milestone> findMilestonesInRange(@Param("userId") Long userId,
                                          @Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate);

    /**
     * Free 일일 생성 한도: 오늘(한국 시간) 사용자가 직접 만든 Task 수 (명세 3 · 7장).
     * 루틴·미션·AI 목표 Task 는 제외. created_at 이 없는 V34 이전 데이터는 세지 않는다.
     */
    @Query("""
        SELECT COUNT(t) FROM Task t
        WHERE t.user.userId = :userId
          AND t.source = com.naleul.naleul.domain.task.enums.TaskSource.USER_INPUT
          AND t.routine IS NULL
          AND t.missionParticipant IS NULL
          AND t.taskStatus <> com.naleul.naleul.domain.task.enums.TaskStatus.DELETED
          AND t.createdAt >= :dayStart AND t.createdAt < :dayEnd
    """)
    long countManualCreatedBetween(@Param("userId") Long userId,
                                   @Param("dayStart") LocalDateTime dayStart,
                                   @Param("dayEnd") LocalDateTime dayEnd);

    /** 이월 흔적(2026-10)을 그리려고 Task 이름 · 색을 한 번에 */
    @Query("""
        SELECT t FROM Task t
        LEFT JOIN FETCH t.goalCategory g
        LEFT JOIN FETCH g.userColor
        WHERE t.user.userId = :userId AND t.taskId IN :taskIds
    """)
    List<Task> findAllOfUserByIds(@Param("userId") Long userId, @Param("taskIds") Collection<Long> taskIds);
}
