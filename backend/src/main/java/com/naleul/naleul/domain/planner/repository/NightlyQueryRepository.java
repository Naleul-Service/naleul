package com.naleul.naleul.domain.planner.repository;

import com.naleul.naleul.domain.task.entity.Task;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 저녁 이월·내일 TimeTable 작업 전용 조회 (기존 TaskRepository 와 분리) */
public interface NightlyQueryRepository extends Repository<Task, Long> {

    /**
     * 22시 작업 대상 사용자: 최근 30일 ~ 내일 사이에 Task 가 있는 사용자 (user_id 커서 페이지).
     * 오래 접속하지 않은 사용자까지 매일 돌지 않도록 범위를 좁힌다.
     */
    @Query(value = """
        SELECT DISTINCT t.user_id FROM task t
        WHERE t.user_id > :afterUserId
          AND t.task_status <> 'DELETED'
          AND ( t.routine_date BETWEEN :fromDate AND :toDate
             OR (t.planned_start_at >= :fromDateTime AND t.planned_start_at < :toDateTime) )
        ORDER BY t.user_id
        LIMIT :limit
    """, nativeQuery = true)
    List<Number> findActiveUserIds(@Param("afterUserId") long afterUserId,
                                   @Param("fromDate") LocalDate fromDate,
                                   @Param("toDate") LocalDate toDate,
                                   @Param("fromDateTime") LocalDateTime fromDateTime,
                                   @Param("toDateTime") LocalDateTime toDateTime,
                                   @Param("limit") int limit);

    /**
     * 사용자별 오늘 일정이 끝나는 시각 + 남은 일 개수 (이월 시각 계산용).
     * [0] user_id, [1] 오늘 시작하는 블록 중 가장 늦게 끝나는 시각 (없으면 null),
     * [2] 오늘 Task 개수, [3] 그중 아직 TODO 인 개수
     */
    @Query(value = """
        SELECT t.user_id,
               MAX(CASE WHEN t.planned_start_at IS NOT NULL
                        THEN COALESCE(t.planned_end_at, t.planned_start_at) END),
               COUNT(*),
               SUM(CASE WHEN t.task_status = 'TODO' THEN 1 ELSE 0 END)
        FROM task t
        WHERE t.user_id IN (:userIds)
          AND t.task_status <> 'DELETED'
          AND ( (t.planned_start_at IS NULL AND t.routine_date = :today)
             OR (t.planned_start_at >= :dayStart AND t.planned_start_at < :dayEnd) )
        GROUP BY t.user_id
    """, nativeQuery = true)
    List<Object[]> findDayEndStats(@Param("userIds") List<Long> userIds,
                                   @Param("today") LocalDate today,
                                   @Param("dayStart") LocalDateTime dayStart,
                                   @Param("dayEnd") LocalDateTime dayEnd);

    /**
     * 이월 후보: 오늘 하기로 했는데 아직 TODO 인 일회성 Task.
     * 루틴(그날 인스턴스)·미션·약속(시간이 지나면 의미 없음)은 이월하지 않는다.
     * 시간이 있는 Task 중 아직 끝나지 않은 것(22시 이후 일정)은 서비스에서 걸러 낸다.
     */
    @Query("""
        SELECT t FROM Task t
        LEFT JOIN FETCH t.goalCategory
        WHERE t.user.userId = :userId
          AND t.taskStatus = com.naleul.naleul.domain.task.enums.TaskStatus.TODO
          AND t.routine IS NULL
          AND t.missionParticipant IS NULL
          AND t.taskKind <> com.naleul.naleul.domain.task.enums.TaskKind.APPOINTMENT
          AND ( (t.plannedStartAt IS NULL AND t.routineDate = :today)
             OR (t.plannedStartAt >= :dayStart AND t.plannedStartAt < :dayEnd) )
    """)
    List<Task> findCarryOverCandidates(@Param("userId") Long userId,
                                       @Param("today") LocalDate today,
                                       @Param("dayStart") LocalDateTime dayStart,
                                       @Param("dayEnd") LocalDateTime dayEnd);
}
