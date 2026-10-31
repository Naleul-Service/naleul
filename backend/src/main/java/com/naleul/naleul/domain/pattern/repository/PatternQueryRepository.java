package com.naleul.naleul.domain.pattern.repository;

import com.naleul.naleul.domain.task.entity.Task;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 나의 패턴 계산 재료 조회 (명세 4 · 8장).
 * 엔티티 전체가 아니라 필요한 컬럼만 Object[] 로 읽는다 — 1년치 수천 건을 1GB 서버 메모리에 올려야 하므로.
 * (PlacementQueryRepository 처럼 Task 에 대한 별도 Repository)
 */
public interface PatternQueryRepository extends Repository<Task, Long> {

    /**
     * 평가 대상 Task (명세 4 · 1-1).
     *  - TODO / COMPLETED 만 (DELETED·SKIPPED 제외), 미션 제외, 삭제된 목표의 Task 제외
     *  - 평가일: 시간이 있으면 planned_start_at 날짜 ∈ [from, to), 없으면 routine_date ∈ [startDate, endDate]
     *
     * 컬럼 순서는 PatternSampleLoader.toRow 와 맞춘다.
     */
    @Query("""
        SELECT t.taskId,
               r.routineId, r.routineName, r.emoji,
               g.goalCategoryId, g.goalCategoryName, g.emoji, uc.colorCode, g.isTemporary, g.goalCategoryStartDate,
               t.taskName, t.emoji, t.taskStatus,
               t.plannedStartAt, t.plannedEndAt, t.plannedDurationMinutes,
               t.routineDate, t.dueDate, t.completedAt, t.carryOverCount
        FROM Task t
        LEFT JOIN t.routine r
        LEFT JOIN t.goalCategory g
        LEFT JOIN g.userColor uc
        WHERE t.user.userId = :userId
          AND t.taskStatus IN (com.naleul.naleul.domain.task.enums.TaskStatus.TODO,
                               com.naleul.naleul.domain.task.enums.TaskStatus.COMPLETED)
          AND t.missionParticipant IS NULL
          AND (g.goalCategoryId IS NULL
               OR g.goalCategoryStatus <> com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus.DELETED)
          AND (
                (t.plannedStartAt >= :from AND t.plannedStartAt < :to)
             OR (t.plannedStartAt IS NULL AND t.routineDate >= :startDate AND t.routineDate <= :endDate)
          )
    """)
    List<Object[]> findEvaluatedTasks(@Param("userId") Long userId,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate);

    /** Task 별 실제 수행 기록 요약: [taskId, 첫 시작, 마지막 종료, 소요 분 합] */
    @Query("""
        SELECT ta.task.taskId, MIN(ta.actualStartAt), MAX(ta.actualEndAt), SUM(ta.actualDurationMinutes)
        FROM TaskActual ta
        WHERE ta.task.taskId IN :taskIds
        GROUP BY ta.task.taskId
    """)
    List<Object[]> findActualSummaries(@Param("taskIds") Collection<Long> taskIds);

    /** 첫 기록 날짜 재료: [가장 이른 planned_start_at, 가장 이른 routine_date] (전체 기간 계산용) */
    @Query("""
        SELECT MIN(t.plannedStartAt), MIN(t.routineDate)
        FROM Task t
        WHERE t.user.userId = :userId
          AND t.taskStatus IN (com.naleul.naleul.domain.task.enums.TaskStatus.TODO,
                               com.naleul.naleul.domain.task.enums.TaskStatus.COMPLETED)
          AND t.missionParticipant IS NULL
    """)
    List<Object[]> findFirstDates(@Param("userId") Long userId);

    /** 가장 최근 완료 시각 — 오늘 리포트 캐시가 그 뒤에 만들어졌는지 확인용 */
    @Query("""
        SELECT MAX(t.completedAt) FROM Task t
        WHERE t.user.userId = :userId
          AND t.taskStatus = com.naleul.naleul.domain.task.enums.TaskStatus.COMPLETED
    """)
    LocalDateTime findLastCompletedAt(@Param("userId") Long userId);

    /**
     * 리포트 캐시가 아직 맞는지 확인하는 재료 (한 번에): [Task 수, 완료 수, 마지막 완료 시각, 마지막 Task id, 지금 지난 오늘 미완료 블록 수].
     * 완료·완료 취소·삭제·새 Task·시간이 지나 "놓침"이 된 블록이 생기면 값이 바뀐다.
     */
    @Query("""
        SELECT COUNT(t),
               SUM(CASE WHEN t.taskStatus = com.naleul.naleul.domain.task.enums.TaskStatus.COMPLETED THEN 1 ELSE 0 END),
               MAX(t.completedAt),
               MAX(t.taskId),
               SUM(CASE WHEN t.taskStatus = com.naleul.naleul.domain.task.enums.TaskStatus.TODO
                         AND t.plannedStartAt >= :todayStart AND t.plannedEndAt < :now THEN 1 ELSE 0 END)
        FROM Task t
        WHERE t.user.userId = :userId
          AND t.taskStatus IN (com.naleul.naleul.domain.task.enums.TaskStatus.TODO,
                               com.naleul.naleul.domain.task.enums.TaskStatus.COMPLETED)
          AND t.missionParticipant IS NULL
    """)
    List<Object[]> findTaskFingerprint(@Param("userId") Long userId,
                                       @Param("todayStart") LocalDateTime todayStart,
                                       @Param("now") LocalDateTime now);

    /** 마지막 일정 변경 기록 id (이동·이월·엔진 배치) */
    @Query("SELECT MAX(l.taskScheduleLogId) FROM TaskScheduleLog l WHERE l.userId = :userId")
    Long findLastScheduleLogId(@Param("userId") Long userId);

    /** 실제 시간 기록: [수, 마지막 id] — "계획과 다른 시간에 했어요" 수정·삭제 반영 */
    @Query("SELECT COUNT(ta), MAX(ta.taskActualId) FROM TaskActual ta WHERE ta.user.userId = :userId")
    List<Object[]> findActualFingerprint(@Param("userId") Long userId);

    /** Task 별 이월 기록 수 (V35 이후): [taskId, count] — 옛 이월 횟수 = carry_over_count − 이 값 */
    @Query("""
        SELECT l.taskId, COUNT(l)
        FROM TaskScheduleLog l
        WHERE l.taskId IN :taskIds
          AND l.action = com.naleul.naleul.domain.pattern.enums.TaskScheduleAction.CARRY_OVER
        GROUP BY l.taskId
    """)
    List<Object[]> countCarryLogs(@Param("taskIds") Collection<Long> taskIds);

    /**
     * 제안 겹침 검사용: [from, to) 에 시작하는, 자동 배치가 옮길 수 없는 블록 [taskId, routineId, 시작, 종료].
     * 잠긴 Task(사용자가 정한 시간, 시간 있는 루틴), 완료한 Task, 미션 Task.
     */
    @Query("""
        SELECT t.taskId, r.routineId, t.plannedStartAt, t.plannedEndAt
        FROM Task t
        LEFT JOIN t.routine r
        WHERE t.user.userId = :userId
          AND t.plannedStartAt >= :from AND t.plannedStartAt < :to
          AND t.plannedEndAt IS NOT NULL
          AND t.taskStatus <> com.naleul.naleul.domain.task.enums.TaskStatus.DELETED
          AND (t.scheduleLocked = true
               OR t.taskStatus = com.naleul.naleul.domain.task.enums.TaskStatus.COMPLETED
               OR t.missionParticipant IS NOT NULL)
    """)
    List<Object[]> findFixedTaskBlocks(@Param("userId") Long userId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);
}
