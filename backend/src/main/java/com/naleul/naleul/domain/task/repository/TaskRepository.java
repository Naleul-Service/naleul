package com.naleul.naleul.domain.task.repository;

import com.naleul.naleul.domain.chart.repository.projection.*;
import com.naleul.naleul.domain.externalCalendar.enums.ExternalCalendarSource;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskPriority;
import com.naleul.naleul.domain.task.enums.TaskSourceType;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.repository.projection.DateCountProjection;
import com.naleul.naleul.domain.task.repository.projection.GoalDoneTaskProjection;
import com.naleul.naleul.domain.task.repository.projection.GoalLastDoneProjection;
import com.naleul.naleul.domain.task.repository.projection.RoutineDoneProjection;
import com.naleul.naleul.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * 특정 유저가 특정 시간 범위(하루) 안에 "직접 생성한" Task 개수를 셈.
     * 루틴에서 자동 생성된 Task(routine != null)와
     * 미션에서 자동 생성된 Task(missionParticipant != null)는 제외.
     * Free 플랜 하루 생성 제한 체크용.
     */
    @Query("SELECT COUNT(t) FROM Task t " +
            "WHERE t.user.userId = :userId " +
            "AND t.routine IS NULL " +
            "AND t.missionParticipant IS NULL " +
            "AND t.taskStatus <> com.naleul.naleul.domain.task.enums.TaskStatus.DELETED " +
            "AND t.plannedStartAt >= :dayStart " +
            "AND t.plannedStartAt < :dayEnd")
    long countManualTasksByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd
    );

    // ✅ 같은 루틴 + 같은 날짜 Task가 이미 있는지 확인 (중복 방지)
    boolean existsByRoutineAndRoutineDate(Routine routine, LocalDate routineDate);

    // ✅ 특정 날짜의 루틴 Tasks 조회
    List<Task> findByUserAndRoutineDateAndRoutineIsNotNull(User user, LocalDate date);

    // existsByMissionParticipantId 제거하고 이걸로 교체
    @Query("SELECT COUNT(t) > 0 FROM Task t WHERE t.missionParticipant.missionParticipantId = :participantId")
    boolean existsByMissionParticipantId(@Param("participantId") Long participantId);

    @Query("""
SELECT t FROM Task t
LEFT JOIN FETCH t.goalCategory
LEFT JOIN FETCH t.generalCategory
WHERE t.user.userId = :userId
""")
    List<Task> findAllByUserIdWithDetails(@Param("userId") Long userId);

    @Query("""
SELECT t FROM Task t
LEFT JOIN FETCH t.goalCategory
LEFT JOIN FETCH t.generalCategory
WHERE t.taskId = :taskId AND t.user.userId = :userId
""")
    Optional<Task> findByTaskIdAndUserIdWithDetails(
            @Param("taskId") Long taskId,
            @Param("userId") Long userId
    );

    @Query("""
SELECT t FROM Task t
LEFT JOIN FETCH t.goalCategory
LEFT JOIN FETCH t.generalCategory
WHERE t.user.userId = :userId
AND (
    (t.plannedStartAt IS NOT NULL
        AND t.plannedStartAt < :kstDayEnd
        AND (
            (t.plannedEndAt IS NOT NULL AND t.plannedEndAt > :kstDayStart)
            OR (t.plannedEndAt IS NULL AND t.plannedStartAt >= :kstDayStart)
        ))
    OR (t.plannedStartAt IS NULL AND t.routineDate = :date)
)
AND (:goalCategoryId IS NULL OR t.goalCategory.goalCategoryId = :goalCategoryId)
AND (:generalCategoryId IS NULL OR t.generalCategory.generalCategoryId = :generalCategoryId)
AND (:priority IS NULL OR t.taskPriority = :priority)
AND (:sourceType IS NULL
    OR (:sourceType = 'ROUTINE' AND t.routine IS NOT NULL)
    OR (:sourceType = 'MISSION' AND t.missionParticipant IS NOT NULL)
    OR (:sourceType = 'MANUAL' AND t.routine IS NULL AND t.missionParticipant IS NULL)
)
ORDER BY t.plannedStartAt ASC
""")
    List<Task> findDailyTasks(
            @Param("userId") Long userId,
            @Param("date") LocalDate date,              // ✅ 새로 추가된 파라미터
            @Param("kstDayStart") LocalDateTime kstDayStart,
            @Param("kstDayEnd") LocalDateTime kstDayEnd,
            @Param("goalCategoryId") Long goalCategoryId,
            @Param("generalCategoryId") Long generalCategoryId,
            @Param("priority") TaskPriority priority,
            @Param("sourceType") String sourceType
    );

    // findWeeklyTasksWithoutPage 수정
    @Query("""
SELECT t FROM Task t
LEFT JOIN FETCH t.goalCategory
LEFT JOIN FETCH t.generalCategory
WHERE t.user.userId = :userId
AND (
    (t.plannedStartAt IS NOT NULL
        AND (:startDate IS NULL OR CAST(t.plannedStartAt AS date) >= :startDate)
        AND (:endDate IS NULL OR CAST(t.plannedStartAt AS date) <= :endDate))
    OR
    (t.plannedStartAt IS NULL
        AND (:startDate IS NULL OR t.routineDate >= :startDate)
        AND (:endDate IS NULL OR t.routineDate <= :endDate))
)
AND (:goalCategoryId IS NULL OR t.goalCategory.goalCategoryId = :goalCategoryId)
AND (:generalCategoryId IS NULL OR t.generalCategory.generalCategoryId = :generalCategoryId)
AND (:priority IS NULL OR t.taskPriority = :priority)
AND (:sourceType IS NULL
    OR (:sourceType = 'ROUTINE' AND t.routine IS NOT NULL)
    OR (:sourceType = 'MISSION' AND t.missionParticipant IS NOT NULL)
    OR (:sourceType = 'MANUAL' AND t.routine IS NULL AND t.missionParticipant IS NULL)
)
ORDER BY t.plannedStartAt ASC
""")
    List<Task> findWeeklyTasksWithoutPage(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("goalCategoryId") Long goalCategoryId,
            @Param("generalCategoryId") Long generalCategoryId,
            @Param("priority") TaskPriority priority,
            @Param("sourceType") String sourceType
    );

    // findMonthlyTasksWithoutPage 수정
    @Query("""
SELECT t FROM Task t
LEFT JOIN FETCH t.goalCategory
LEFT JOIN FETCH t.generalCategory
WHERE t.user.userId = :userId
AND (
    (t.plannedStartAt IS NOT NULL AND (
        (YEAR(t.plannedStartAt) = :year AND MONTH(t.plannedStartAt) = :month)
        OR
        (t.plannedEndAt IS NOT NULL AND YEAR(t.plannedEndAt) = :year AND MONTH(t.plannedEndAt) = :month)
    ))
    OR
    (t.plannedStartAt IS NULL
        AND YEAR(t.routineDate) = :year AND MONTH(t.routineDate) = :month)
)
AND (:sourceType IS NULL
    OR (:sourceType = 'ROUTINE' AND t.routine IS NOT NULL)
    OR (:sourceType = 'MISSION' AND t.missionParticipant IS NOT NULL)
    OR (:sourceType = 'MANUAL' AND t.routine IS NULL AND t.missionParticipant IS NULL)
)
ORDER BY t.plannedStartAt ASC
""")
    List<Task> findMonthlyTasksWithoutPage(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("sourceType") String sourceType
    );

    @Query("""
SELECT t FROM Task t
LEFT JOIN FETCH t.goalCategory
LEFT JOIN FETCH t.generalCategory
WHERE t.user.userId = :userId
AND t.plannedStartAt < :endDateTime
AND t.plannedEndAt > :startDateTime
ORDER BY t.plannedStartAt ASC
""")
    Page<Task> findByTimeRange(
            @Param("userId") Long userId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            Pageable pageable
    );

    @Query(value = """
    SELECT
        COUNT(t.task_id) AS totalCount,
        SUM(CASE
            WHEN ta.actual_start_at IS NOT NULL
             AND ta.actual_end_at IS NOT NULL
             AND t.planned_start_at IS NOT NULL
             AND t.planned_end_at IS NOT NULL
             AND (
                 TIMESTAMPDIFF(SECOND,
                     GREATEST(t.planned_start_at, ta.actual_start_at),
                     LEAST(t.planned_end_at, ta.actual_end_at)
                 ) * 1.0
                 / NULLIF(TIMESTAMPDIFF(SECOND, t.planned_start_at, t.planned_end_at), 0)
             ) >= 0.5
            THEN 1 ELSE 0
        END) AS achievedCount
    FROM task t
    LEFT JOIN task_actual ta ON ta.task_id = t.task_id
    WHERE t.user_id = :userId
""", nativeQuery = true)
    AchievementStatProjection findAchievementStats(@Param("userId") Long userId);

    /**
     * 목표의 완료한 Task 전부 (일반 + 루틴) — 기록형 목표 "나의 패턴"·"업무 일지"의 주 데이터.
     * 루틴은 LEFT JOIN — t.routine.routineId 로 쓰면 내부 조인이 돼서 일반 Task 가 빠진다.
     */
    @Query("""
    SELECT t.taskId AS taskId, t.taskName AS taskName, t.emoji AS emoji, r.routineId AS routineId,
           t.actualStartAt AS actualStartAt, t.actualEndAt AS actualEndAt,
           t.plannedStartAt AS plannedStartAt, t.plannedEndAt AS plannedEndAt,
           t.plannedDurationMinutes AS plannedDurationMinutes,
           t.routineDate AS routineDate, t.scheduledDate AS scheduledDate, t.completedAt AS completedAt
    FROM Task t
    LEFT JOIN t.routine r
    WHERE t.user.userId = :userId
      AND t.goalCategory.goalCategoryId = :goalCategoryId
      AND t.taskStatus = 'COMPLETED'
""")
    List<GoalDoneTaskProjection> findCompletedOfGoal(@Param("userId") Long userId,
                                                     @Param("goalCategoryId") Long goalCategoryId);

    /** 완료한 루틴 Task 의 날짜·실제 시각·계획 시간 (기록형 목표 패턴의 "어떤 루틴에 시간을 썼나요?") */
    @Query("""
    SELECT t.routine.routineId AS routineId, t.routineDate AS routineDate,
           t.actualStartAt AS actualStartAt, t.actualEndAt AS actualEndAt,
           t.plannedDurationMinutes AS plannedDurationMinutes
    FROM Task t
    WHERE t.routine.routineId IN :routineIds
      AND t.taskStatus = 'COMPLETED'
      AND t.routineDate IS NOT NULL
""")
    List<RoutineDoneProjection> findCompletedRoutineTasks(@Param("routineIds") List<Long> routineIds);

    // 루틴 ID 목록에 해당하고 COMPLETED인 Task의 날짜만 조회
    @Query("""
    SELECT t.routineDate FROM Task t
    WHERE t.routine.routineId IN :routineIds
      AND t.taskStatus = 'COMPLETED'
      AND t.routineDate IS NOT NULL
""")
    List<LocalDate> findCompletedDatesByRoutineIds(@Param("routineIds") List<Long> routineIds);

    // ✅ 여기로 이동
    @Query("""
        SELECT t.routine.routineId, t.routineDate FROM Task t
        WHERE t.routine.routineId IN :routineIds
          AND t.taskStatus = 'COMPLETED'
          AND t.routineDate IS NOT NULL
    """)
    List<Object[]> findCompletedDatesWithRoutineId(@Param("routineIds") List<Long> routineIds);

    /**
     * 루틴별 "실제로 한 시각" (2026-10, 목표 상세 · 루틴을 주로 몇 시에 했는지).
     * [routineId, taskId, plannedStartAt, plannedEndAt, completedAt, actualStartAt(task_actual), actualEndAt(task_actual)]
     * task_actual 이 여러 줄(나눠서 한 기록)이면 줄마다 한 행.
     */
    @Query("""
        SELECT t.routine.routineId, t.taskId, t.plannedStartAt, t.plannedEndAt, t.completedAt,
               a.actualStartAt, a.actualEndAt
        FROM Task t LEFT JOIN t.taskActuals a
        WHERE t.routine.routineId IN :routineIds
          AND t.taskStatus = com.naleul.naleul.domain.task.enums.TaskStatus.COMPLETED
    """)
    List<Object[]> findCompletedPracticeTimesByRoutineIds(@Param("routineIds") List<Long> routineIds);

    /**
     * 루틴 실천 알림용 (2026-10): 한 사용자의 진행 중 루틴 인스턴스 [routineId, taskId, routineDate, taskStatus]
     * since <= routineDate < before, 지운 Task 는 뺀다.
     */
    @Query("""
        SELECT r.routineId, t.taskId, t.routineDate, t.taskStatus
        FROM Task t JOIN t.routine r
        WHERE t.user.userId = :userId
          AND r.routineStatus IN (com.naleul.naleul.domain.routine.enums.RoutineStatus.NOT_STARTED,
                                  com.naleul.naleul.domain.routine.enums.RoutineStatus.IN_PROGRESS)
          AND t.routineDate >= :since AND t.routineDate < :before
          AND t.taskStatus <> com.naleul.naleul.domain.task.enums.TaskStatus.DELETED
    """)
    List<Object[]> findRoutineHistoryOfUser(@Param("userId") Long userId,
                                            @Param("since") LocalDate since,
                                            @Param("before") LocalDate before);

    /** findCompletedPracticeTimesByRoutineIds 와 같은 모양, 특정 Task 만 */
    @Query("""
        SELECT t.routine.routineId, t.taskId, t.plannedStartAt, t.plannedEndAt, t.completedAt,
               a.actualStartAt, a.actualEndAt
        FROM Task t LEFT JOIN t.taskActuals a
        WHERE t.taskId IN :taskIds
          AND t.taskStatus = com.naleul.naleul.domain.task.enums.TaskStatus.COMPLETED
    """)
    List<Object[]> findPracticeTimesByTaskIds(@Param("taskIds") List<Long> taskIds);

    /** 오늘 아직 안 한(TODO) 진행 중 루틴 Task — 알림을 켜 둔 것만 */
    @Query("""
        SELECT t FROM Task t
        JOIN FETCH t.user
        JOIN FETCH t.routine r
        WHERE t.routineDate = :today
          AND t.taskStatus = com.naleul.naleul.domain.task.enums.TaskStatus.TODO
          AND t.notificationEnabled = true
          AND r.routineStatus IN (com.naleul.naleul.domain.routine.enums.RoutineStatus.NOT_STARTED,
                                  com.naleul.naleul.domain.routine.enums.RoutineStatus.IN_PROGRESS)
    """)
    List<Task> findOpenRoutineTasksOn(@Param("today") LocalDate today);

    // ✅ default 메서드도 같이
    default Map<Long, List<LocalDate>> findCompletedDatesGroupedByRoutineId(List<Long> routineIds) {
        return findCompletedDatesWithRoutineId(routineIds).stream()
                .collect(Collectors.groupingBy(
                        row -> (Long) row[0],
                        Collectors.mapping(row -> (LocalDate) row[1], Collectors.toList())
                ));
    }

    // 특정 미션 참여자의 기간 내 Task 전체 조회 (잔디맵용)
    @Query("""
        SELECT t FROM Task t
        WHERE t.missionParticipant.mission.missionId = :missionId
        AND t.user.userId = :userId
        AND t.routineDate BETWEEN :startDate AND :endDate
        """)
    List<Task> findMissionTasks(
            @Param("missionId") Long missionId,
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // 특정 미션의 특정 날짜에 완료한 유저 수 조회
    @Query("""
        SELECT t.routineDate, COUNT(t)
        FROM Task t
        WHERE t.missionParticipant.mission.missionId = :missionId
        AND t.taskStatus = 'COMPLETED'
        AND t.routineDate BETWEEN :startDate AND :endDate
        GROUP BY t.routineDate
        """)
    List<Object[]> countCompletedByDate(
            @Param("missionId") Long missionId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // 특정 미션 참여자의 전체 Task 조회 (완료 여부 포함)
    @Query("""
        SELECT t FROM Task t
        LEFT JOIN FETCH t.missionParticipant mp
        WHERE mp.mission.missionId = :missionId
        AND t.user.userId = :userId
        ORDER BY t.routineDate ASC
        """)
    List<Task> findAllMissionTasks(
            @Param("missionId") Long missionId,
            @Param("userId") Long userId
    );

    // 특정 미션 참여자의 오늘 Task 조회
    @Query("""
        SELECT t FROM Task t
        WHERE t.missionParticipant.missionParticipantId = :participantId
        AND t.routineDate = :date
        """)
    Optional<Task> findTodayMissionTask(
            @Param("participantId") Long participantId,
            @Param("date") LocalDate date
    );

    // TaskRepository.java에 추가
    @Query("""
    SELECT t FROM Task t
    JOIN FETCH t.missionParticipant mp
    JOIN FETCH mp.mission m
    WHERE t.user.userId = :userId
      AND m.missionId = :missionId
      AND t.routineDate = :date
""")
    Optional<Task> findMissionTaskByUserIdAndMissionIdAndDate(
            @Param("userId") Long userId,
            @Param("missionId") Long missionId,
            @Param("date") LocalDate date
    );

    // 계획 달성률: routine/mission 아닌 순수 계획 Task만 대상
    // ⚠️ goal_category가 DELETED면 제외 (goal_category_id가 없는 Task는 그대로 포함)
    @Query(value = """
    SELECT
        COUNT(t.task_id) AS totalCount,
        SUM(CASE
            WHEN ta.actual_start_at IS NOT NULL
             AND ta.actual_end_at IS NOT NULL
             AND t.planned_start_at IS NOT NULL
             AND t.planned_end_at IS NOT NULL
             AND (
                 TIMESTAMPDIFF(SECOND,
                     GREATEST(t.planned_start_at, ta.actual_start_at),
                     LEAST(t.planned_end_at, ta.actual_end_at)
                 ) * 1.0
                 / NULLIF(TIMESTAMPDIFF(SECOND, t.planned_start_at, t.planned_end_at), 0)
             ) >= 0.5
            THEN 1 ELSE 0
        END) AS achievedCount
    FROM task t
    LEFT JOIN task_actual ta ON ta.task_id = t.task_id
    LEFT JOIN goal_category gc ON gc.goal_category_id = t.goal_category_id
    WHERE t.user_id = :userId
      AND t.routine_id IS NULL
      AND t.mission_participant_id IS NULL
      AND t.task_status <> 'DELETED'
      AND (t.goal_category_id IS NULL OR gc.goal_category_status <> 'DELETED')
      AND (:startDateTime IS NULL OR t.planned_start_at >= :startDateTime)
      AND (:endDateTime IS NULL OR t.planned_start_at < :endDateTime)
""", nativeQuery = true)
    AchievementStatProjection findPlanAchievementStats(
            @Param("userId") Long userId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    // 루틴 달성률: routine으로 생성된 Task만 대상
    // ⚠️ goal_category가 DELETED면 제외
    @Query(value = """
    SELECT
        COUNT(t.task_id) AS totalCount,
        SUM(CASE
            WHEN ta.actual_start_at IS NOT NULL
             AND ta.actual_end_at IS NOT NULL
             AND t.planned_start_at IS NOT NULL
             AND t.planned_end_at IS NOT NULL
             AND (
                 TIMESTAMPDIFF(SECOND,
                     GREATEST(t.planned_start_at, ta.actual_start_at),
                     LEAST(t.planned_end_at, ta.actual_end_at)
                 ) * 1.0
                 / NULLIF(TIMESTAMPDIFF(SECOND, t.planned_start_at, t.planned_end_at), 0)
             ) >= 0.5
            THEN 1 ELSE 0
        END) AS achievedCount
    FROM task t
    LEFT JOIN task_actual ta ON ta.task_id = t.task_id
    LEFT JOIN goal_category gc ON gc.goal_category_id = t.goal_category_id
    WHERE t.user_id = :userId
      AND t.routine_id IS NOT NULL
      AND t.task_status <> 'DELETED'
      AND (t.goal_category_id IS NULL OR gc.goal_category_status <> 'DELETED')
      AND (:startDateTime IS NULL OR t.planned_start_at >= :startDateTime)
      AND (:endDateTime IS NULL OR t.planned_start_at < :endDateTime)
""", nativeQuery = true)
    AchievementStatProjection findRoutineAchievementStats(
            @Param("userId") Long userId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    // goalCategory별 계획 달성 통계 (전체 목표 한번에, N+1 방지)
    // ⚠️ 기간 필터(startDateTime/endDateTime) + goalCategoryStatus + DELETED 제외 추가
    //    (달성률 도넛의 "목표별 breakdown"을 만들기 위해 status를 같이 SELECT함)
    @Query(value = """
    SELECT
        t.goal_category_id AS goalCategoryId,
        gc.goal_category_status AS goalCategoryStatus,
        COUNT(t.task_id) AS totalCount,
        SUM(CASE
            WHEN ta.actual_start_at IS NOT NULL
             AND ta.actual_end_at IS NOT NULL
             AND t.planned_start_at IS NOT NULL
             AND t.planned_end_at IS NOT NULL
             AND (
                 TIMESTAMPDIFF(SECOND,
                     GREATEST(t.planned_start_at, ta.actual_start_at),
                     LEAST(t.planned_end_at, ta.actual_end_at)
                 ) * 1.0
                 / NULLIF(TIMESTAMPDIFF(SECOND, t.planned_start_at, t.planned_end_at), 0)
             ) >= 0.5
            THEN 1 ELSE 0
        END) AS achievedCount
    FROM task t
    LEFT JOIN task_actual ta ON ta.task_id = t.task_id
    JOIN goal_category gc ON gc.goal_category_id = t.goal_category_id
    WHERE t.user_id = :userId
      AND t.routine_id IS NULL
      AND t.mission_participant_id IS NULL
      AND t.task_status <> 'DELETED'
      AND gc.goal_category_status <> 'DELETED'
      AND t.goal_category_id IS NOT NULL
      AND (:startDateTime IS NULL OR t.planned_start_at >= :startDateTime)
      AND (:endDateTime IS NULL OR t.planned_start_at < :endDateTime)
    GROUP BY t.goal_category_id, gc.goal_category_status
""", nativeQuery = true)
    List<GoalAchievementStatProjection> findPlanAchievementStatsGroupedByGoal(
            @Param("userId") Long userId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    // goalCategory별 루틴 달성 통계 (전체 목표 한번에, N+1 방지)
    // ⚠️ 기간 필터 + goalCategoryStatus + DELETED 제외 추가 (위와 동일한 이유)
    @Query(value = """
    SELECT
        t.goal_category_id AS goalCategoryId,
        gc.goal_category_status AS goalCategoryStatus,
        COUNT(t.task_id) AS totalCount,
        SUM(CASE
            WHEN ta.actual_start_at IS NOT NULL
             AND ta.actual_end_at IS NOT NULL
             AND t.planned_start_at IS NOT NULL
             AND t.planned_end_at IS NOT NULL
             AND (
                 TIMESTAMPDIFF(SECOND,
                     GREATEST(t.planned_start_at, ta.actual_start_at),
                     LEAST(t.planned_end_at, ta.actual_end_at)
                 ) * 1.0
                 / NULLIF(TIMESTAMPDIFF(SECOND, t.planned_start_at, t.planned_end_at), 0)
             ) >= 0.5
            THEN 1 ELSE 0
        END) AS achievedCount
    FROM task t
    LEFT JOIN task_actual ta ON ta.task_id = t.task_id
    JOIN goal_category gc ON gc.goal_category_id = t.goal_category_id
    WHERE t.user_id = :userId
      AND t.routine_id IS NOT NULL
      AND t.task_status <> 'DELETED'
      AND gc.goal_category_status <> 'DELETED'
      AND t.goal_category_id IS NOT NULL
      AND (:startDateTime IS NULL OR t.planned_start_at >= :startDateTime)
      AND (:endDateTime IS NULL OR t.planned_start_at < :endDateTime)
    GROUP BY t.goal_category_id, gc.goal_category_status
""", nativeQuery = true)
    List<GoalAchievementStatProjection> findRoutineAchievementStatsGroupedByGoal(
            @Param("userId") Long userId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    // ✅ generalCategory 하나의 실천률 (루틴 Task + 계획 Task 모두 포함, 기간 옵션)
    @Query(value = """
    SELECT
        COUNT(t.task_id) AS totalCount,
        SUM(CASE
            WHEN ta.actual_start_at IS NOT NULL
             AND ta.actual_end_at IS NOT NULL
             AND t.planned_start_at IS NOT NULL
             AND t.planned_end_at IS NOT NULL
             AND (
                 TIMESTAMPDIFF(SECOND,
                     GREATEST(t.planned_start_at, ta.actual_start_at),
                     LEAST(t.planned_end_at, ta.actual_end_at)
                 ) * 1.0
                 / NULLIF(TIMESTAMPDIFF(SECOND, t.planned_start_at, t.planned_end_at), 0)
             ) >= 0.5
            THEN 1 ELSE 0
        END) AS achievedCount
    FROM task t
    LEFT JOIN task_actual ta ON ta.task_id = t.task_id
    WHERE t.general_category_id = :generalCategoryId
      AND t.task_status <> 'DELETED'
      AND (:startDateTime IS NULL OR t.planned_start_at >= :startDateTime)
      AND (:endDateTime IS NULL OR t.planned_start_at < :endDateTime)
""", nativeQuery = true)
    AchievementStatProjection findGeneralCategoryAchievementStats(
            @Param("generalCategoryId") Long generalCategoryId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    // goalCategory 안의 routine별 개별 실천률 (전체 루틴 한번에, N+1 방지, 기간 옵션)
    @Query(value = """
    SELECT
        t.routine_id AS routineId,
        COUNT(t.task_id) AS totalCount,
        SUM(CASE
            WHEN ta.actual_start_at IS NOT NULL
             AND ta.actual_end_at IS NOT NULL
             AND t.planned_start_at IS NOT NULL
             AND t.planned_end_at IS NOT NULL
             AND (
                 TIMESTAMPDIFF(SECOND,
                     GREATEST(t.planned_start_at, ta.actual_start_at),
                     LEAST(t.planned_end_at, ta.actual_end_at)
                 ) * 1.0
                 / NULLIF(TIMESTAMPDIFF(SECOND, t.planned_start_at, t.planned_end_at), 0)
             ) >= 0.5
            THEN 1 ELSE 0
        END) AS achievedCount
    FROM task t
    LEFT JOIN task_actual ta ON ta.task_id = t.task_id
    WHERE t.goal_category_id = :goalCategoryId
      AND t.routine_id IS NOT NULL
      AND t.task_status <> 'DELETED'
      AND (:startDateTime IS NULL OR t.planned_start_at >= :startDateTime)
      AND (:endDateTime IS NULL OR t.planned_start_at < :endDateTime)
    GROUP BY t.routine_id
""", nativeQuery = true)
    List<RoutineAchievementStatProjection> findRoutineAchievementStatsGroupedByRoutine(
            @Param("goalCategoryId") Long goalCategoryId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    // ✅ 0~23시 시간대별 Task 개수 & 달성 개수 (전체 목표 무관, 기간=1주일)
    // "수면시간 제외"는 별도 조건 없이 자연스럽게 처리됨:
    // Task가 없는 시간대는 애초에 GROUP BY 결과에 행 자체가 안 생기므로 서비스 레이어에서 걸러짐
    // ⚠️ goal_category가 DELETED인 Task는 제외 (goal_category가 없는 Task는 그대로 포함)
    @Query(value = """
    SELECT
        HOUR(DATE_ADD(t.planned_start_at, INTERVAL 9 HOUR)) AS hour,
        COUNT(t.task_id) AS totalCount,
        SUM(CASE
            WHEN ta.actual_start_at IS NOT NULL
             AND ta.actual_end_at IS NOT NULL
             AND (
                 TIMESTAMPDIFF(SECOND,
                     GREATEST(t.planned_start_at, ta.actual_start_at),
                     LEAST(t.planned_end_at, ta.actual_end_at)
                 ) * 1.0
                 / NULLIF(TIMESTAMPDIFF(SECOND, t.planned_start_at, t.planned_end_at), 0)
             ) >= 0.5
            THEN 1 ELSE 0
        END) AS achievedCount
    FROM task t
    LEFT JOIN task_actual ta ON ta.task_id = t.task_id
    LEFT JOIN goal_category gc ON gc.goal_category_id = t.goal_category_id
    WHERE t.user_id = :userId
      AND t.task_status <> 'DELETED'
      AND (t.goal_category_id IS NULL OR gc.goal_category_status <> 'DELETED')
      AND t.planned_start_at IS NOT NULL
      AND t.planned_end_at IS NOT NULL
      AND t.planned_start_at >= :startDateTime
      AND t.planned_start_at < :endDateTime
    GROUP BY HOUR(DATE_ADD(t.planned_start_at, INTERVAL 9 HOUR))
""", nativeQuery = true)
    List<HourlyStatProjection> findHourlyPatternStats(
            @Param("userId") Long userId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    // ✅ goalCategory별 (계획+루틴 Task 모두 포함) 주간 달성 통계
    // 목표별 순위, 지난주 대비 비교에 공통으로 재사용
    // ⚠️ DELETED 목표 제외 추가 (이미 goal_category gc를 JOIN하고 있어서 조건만 추가하면 됨)
    @Query(value = """
    SELECT
        t.goal_category_id AS goalCategoryId,
        gc.goal_category_name AS goalCategoryName,
        COUNT(t.task_id) AS totalCount,
        SUM(CASE
            WHEN ta.actual_start_at IS NOT NULL
             AND ta.actual_end_at IS NOT NULL
             AND t.planned_start_at IS NOT NULL
             AND t.planned_end_at IS NOT NULL
             AND (
                 TIMESTAMPDIFF(SECOND,
                     GREATEST(t.planned_start_at, ta.actual_start_at),
                     LEAST(t.planned_end_at, ta.actual_end_at)
                 ) * 1.0
                 / NULLIF(TIMESTAMPDIFF(SECOND, t.planned_start_at, t.planned_end_at), 0)
             ) >= 0.5
            THEN 1 ELSE 0
        END) AS achievedCount
    FROM task t
    JOIN goal_category gc ON gc.goal_category_id = t.goal_category_id
    LEFT JOIN task_actual ta ON ta.task_id = t.task_id
    WHERE t.user_id = :userId
      AND t.goal_category_id IS NOT NULL
      AND t.task_status <> 'DELETED'
      AND gc.goal_category_status <> 'DELETED'
      AND t.planned_start_at >= :startDateTime
      AND t.planned_start_at < :endDateTime
    GROUP BY t.goal_category_id, gc.goal_category_name
""", nativeQuery = true)
    List<GoalWeeklyStatProjection> findGoalWeeklyAchievementStats(
            @Param("userId") Long userId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    // ✅ 요일별(0=월~6=일) 완료된 루틴 Task 개수 — 선그래프용
    // ⚠️ goal_category DELETED 필터는 아직 안 넣었습니다 — 이 쿼리는 goal_category를 조인하지 않고
    //    routine을 거쳐야 goalCategory에 닿는데, Routine 엔티티의 goalCategory 연관관계 컬럼명을
    //    몰라서 잘못된 조인을 넣느니 일단 보류했어요. 이것도 막고 싶으시면 Routine 엔티티를 보여주세요.
    @Query(value = """
    SELECT
        WEEKDAY(t.planned_start_at) AS weekday,
        COUNT(t.task_id) AS count
    FROM task t
    WHERE t.user_id = :userId
      AND t.routine_id IS NOT NULL
      AND t.task_status = 'COMPLETED'
      AND t.routine_date BETWEEN :weekStart AND :weekEnd
    GROUP BY WEEKDAY(t.planned_start_at)
""", nativeQuery = true)
    List<WeekdayCountProjection> findRoutineWeekdayCompletionCounts(
            @Param("userId") Long userId,
            @Param("weekStart") LocalDate weekStart,
            @Param("weekEnd") LocalDate weekEnd
    );

    // ✅ 루틴별 x 날짜별 상태 (히트맵용 — 어느 루틴이 어느 날 완료됐는지 원본 데이터)
    // ⚠️ 위와 같은 이유로 DELETED 목표 필터는 보류
    @Query("""
        SELECT t.routine.routineId AS routineId,
               t.routine.routineName AS routineName,
               t.routineDate AS routineDate,
               CAST(t.taskStatus AS string) AS taskStatus
        FROM Task t
        WHERE t.user.userId = :userId
          AND t.routine IS NOT NULL
          AND t.routineDate BETWEEN :weekStart AND :weekEnd
    """)
    List<RoutineDailyCompletionProjection> findRoutineDailyCompletionsInWeek(
            @Param("userId") Long userId,
            @Param("weekStart") LocalDate weekStart,
            @Param("weekEnd") LocalDate weekEnd
    );

    // ✅ goalCategory별 주간 "계획/루틴 분리" 달성 통계 (월간 재사용을 위해 주간에서 미리 저장해둠)
    // ⚠️ DELETED 목표 제외 추가
    @Query(value = """
    SELECT
        t.goal_category_id AS goalCategoryId,
        gc.goal_category_name AS goalCategoryName,
        SUM(CASE WHEN t.routine_id IS NULL AND t.mission_participant_id IS NULL THEN 1 ELSE 0 END) AS planTotalCount,
        SUM(CASE
            WHEN t.routine_id IS NULL AND t.mission_participant_id IS NULL
             AND ta.actual_start_at IS NOT NULL AND ta.actual_end_at IS NOT NULL
             AND t.planned_start_at IS NOT NULL AND t.planned_end_at IS NOT NULL
             AND (
                 TIMESTAMPDIFF(SECOND, GREATEST(t.planned_start_at, ta.actual_start_at), LEAST(t.planned_end_at, ta.actual_end_at)) * 1.0
                 / NULLIF(TIMESTAMPDIFF(SECOND, t.planned_start_at, t.planned_end_at), 0)
             ) >= 0.5
            THEN 1 ELSE 0
        END) AS planAchievedCount,
        SUM(CASE WHEN t.routine_id IS NOT NULL THEN 1 ELSE 0 END) AS routineTotalCount,
        SUM(CASE
            WHEN t.routine_id IS NOT NULL
             AND ta.actual_start_at IS NOT NULL AND ta.actual_end_at IS NOT NULL
             AND t.planned_start_at IS NOT NULL AND t.planned_end_at IS NOT NULL
             AND (
                 TIMESTAMPDIFF(SECOND, GREATEST(t.planned_start_at, ta.actual_start_at), LEAST(t.planned_end_at, ta.actual_end_at)) * 1.0
                 / NULLIF(TIMESTAMPDIFF(SECOND, t.planned_start_at, t.planned_end_at), 0)
             ) >= 0.5
            THEN 1 ELSE 0
        END) AS routineAchievedCount
    FROM task t
    JOIN goal_category gc ON gc.goal_category_id = t.goal_category_id
    LEFT JOIN task_actual ta ON ta.task_id = t.task_id
    WHERE t.user_id = :userId
      AND t.goal_category_id IS NOT NULL
      AND t.task_status <> 'DELETED'
      AND gc.goal_category_status <> 'DELETED'
      AND t.planned_start_at >= :startDateTime
      AND t.planned_start_at < :endDateTime
    GROUP BY t.goal_category_id, gc.goal_category_name
""", nativeQuery = true)
    List<GoalWeeklyPlanRoutineStatProjection> findGoalWeeklyPlanRoutineStats(
            @Param("userId") Long userId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    // ══════════════════════════════════════════════════════
    // ✅ 목표 완료 리포트용 (goalCategoryId 기준, 전체 생애 통계)
    // 이미 특정 goalCategoryId 하나로 좁혀 조회하는 화면(완료 리포트/상세 탭)이라
    // DELETED 필터는 넣지 않았습니다 — 완료된 목표를 보는 화면이므로 실질적 위험이 낮음
    // ══════════════════════════════════════════════════════

    // 헤더용: 계획+루틴 통합 전체 실천 횟수 & 달성 개수
    @Query(value = """
    SELECT
        COUNT(t.task_id) AS totalCount,
        SUM(CASE
            WHEN ta.actual_start_at IS NOT NULL
             AND ta.actual_end_at IS NOT NULL
             AND t.planned_start_at IS NOT NULL
             AND t.planned_end_at IS NOT NULL
             AND (
                 TIMESTAMPDIFF(SECOND, GREATEST(t.planned_start_at, ta.actual_start_at), LEAST(t.planned_end_at, ta.actual_end_at)) * 1.0
                 / NULLIF(TIMESTAMPDIFF(SECOND, t.planned_start_at, t.planned_end_at), 0)
             ) >= 0.5
            THEN 1 ELSE 0
        END) AS achievedCount
    FROM task t
    LEFT JOIN task_actual ta ON ta.task_id = t.task_id
    WHERE t.goal_category_id = :goalCategoryId
      AND t.task_status <> 'DELETED'
""", nativeQuery = true)
    AchievementStatProjection findGoalCategoryOverallAchievementStats(@Param("goalCategoryId") Long goalCategoryId);

    // ① 시간대별(0~23시) 계획+루틴 통합 통계 — goalCategory 전체 생애 기준
    @Query(value = """
    SELECT
        HOUR(DATE_ADD(t.planned_start_at, INTERVAL 9 HOUR)) AS hour,
        COUNT(t.task_id) AS totalCount,
        SUM(CASE
            WHEN ta.actual_start_at IS NOT NULL
             AND ta.actual_end_at IS NOT NULL
             AND (
                 TIMESTAMPDIFF(SECOND, GREATEST(t.planned_start_at, ta.actual_start_at), LEAST(t.planned_end_at, ta.actual_end_at)) * 1.0
                 / NULLIF(TIMESTAMPDIFF(SECOND, t.planned_start_at, t.planned_end_at), 0)
             ) >= 0.5
            THEN 1 ELSE 0
        END) AS achievedCount
    FROM task t
    LEFT JOIN task_actual ta ON ta.task_id = t.task_id
    WHERE t.goal_category_id = :goalCategoryId
      AND t.task_status <> 'DELETED'
      AND t.planned_start_at IS NOT NULL
      AND t.planned_end_at IS NOT NULL
    GROUP BY HOUR(DATE_ADD(t.planned_start_at, INTERVAL 9 HOUR))
""", nativeQuery = true)
    List<HourlyStatProjection> findHourlyPatternStatsByGoalCategory(@Param("goalCategoryId") Long goalCategoryId);

    // ② 요일별(0=월~6=일) 계획+루틴 통합 Task 개수 — goalCategory 전체 생애 기준
    @Query(value = """
    SELECT
        WEEKDAY(t.planned_start_at) AS weekday,
        COUNT(t.task_id) AS count
    FROM task t
    WHERE t.goal_category_id = :goalCategoryId
      AND t.task_status <> 'DELETED'
      AND t.planned_start_at IS NOT NULL
    GROUP BY WEEKDAY(t.planned_start_at)
""", nativeQuery = true)
    List<WeekdayCountProjection> findWeekdayPatternStatsByGoalCategory(@Param("goalCategoryId") Long goalCategoryId);

    // ③ 날짜별 Task 개수 — 7등분 구간 계산의 재료 (자바에서 날짜 범위를 나눠서 합산)
    @Query(value = """
    SELECT
        CAST(t.planned_start_at AS DATE) AS taskDate,
        COUNT(t.task_id) AS count
    FROM task t
    WHERE t.goal_category_id = :goalCategoryId
      AND t.task_status <> 'DELETED'
      AND t.planned_start_at IS NOT NULL
    GROUP BY CAST(t.planned_start_at AS DATE)
""", nativeQuery = true)
    List<DateCountProjection> findDailyTaskCountsByGoalCategory(@Param("goalCategoryId") Long goalCategoryId);

    // ⑤ generalCategory별 달성 Task 개수 (계획+루틴 통합) — 세부목표 랭킹용
    @Query(value = """
    SELECT
        t.general_category_id AS generalCategoryId,
        gc.general_category_name AS generalCategoryName,
        SUM(CASE
            WHEN ta.actual_start_at IS NOT NULL
             AND ta.actual_end_at IS NOT NULL
             AND (
                 TIMESTAMPDIFF(SECOND, GREATEST(t.planned_start_at, ta.actual_start_at), LEAST(t.planned_end_at, ta.actual_end_at)) * 1.0
                 / NULLIF(TIMESTAMPDIFF(SECOND, t.planned_start_at, t.planned_end_at), 0)
             ) >= 0.5
            THEN 1 ELSE 0
        END) AS achievedCount
    FROM task t
    JOIN general_category gc ON gc.general_category_id = t.general_category_id
    LEFT JOIN task_actual ta ON ta.task_id = t.task_id
    WHERE t.goal_category_id = :goalCategoryId
      AND t.general_category_id IS NOT NULL
      AND t.task_status <> 'DELETED'
    GROUP BY t.general_category_id, gc.general_category_name
""", nativeQuery = true)
    List<GeneralCategoryExecutionStatProjection> findGeneralCategoryExecutionStats(@Param("goalCategoryId") Long goalCategoryId);

    @Query("""
    SELECT t FROM Task t
    LEFT JOIN FETCH t.taskActuals
    WHERE t.taskId = :taskId AND t.user.userId = :userId
""")
    Optional<Task> findByTaskIdAndUserIdWithActuals(
            @Param("taskId") Long taskId,
            @Param("userId") Long userId
    );

    /**
     * 시작 알림 대상 — 지금 1분 창에 시작하는, 아직 안 한(TODO) Task.
     * 지운 루틴 · 멈춘 루틴 · 지운 목표에 남아 있던 Task 는 보내지 않는다
     * (루틴을 지워도 소프트 삭제 경로에 따라 TODO Task 가 남아 있으면 알림이 계속 가던 문제).
     */
    @Query("""
    SELECT t FROM Task t
    JOIN FETCH t.user
    LEFT JOIN t.routine r
    LEFT JOIN t.goalCategory g
    WHERE t.plannedStartAt >= :windowStart
      AND t.plannedStartAt < :windowEnd
      AND t.startNotified = false
      AND t.notificationEnabled = true
      AND t.taskStatus = com.naleul.naleul.domain.task.enums.TaskStatus.TODO
      AND (r IS NULL OR r.routineStatus NOT IN (
            com.naleul.naleul.domain.routine.enums.RoutineStatus.DELETED,
            com.naleul.naleul.domain.routine.enums.RoutineStatus.STOPPED))
      AND (g IS NULL OR g.goalCategoryStatus <> com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus.DELETED)
    """)
    List<Task> findTasksStartingInWindow(
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd
    );

    // 특정 루틴에 속한 Task 전체 조회 (완료/미완료 구분 없이)
    List<Task> findByRoutine(Routine routine);

    /** 목표에 속한 Task 전부 (이미 삭제 상태인 것 제외) — 목표 삭제 시 "지금 이후" Task 정리용 */
    @Query("""
        SELECT t FROM Task t
        WHERE t.goalCategory.goalCategoryId = :goalCategoryId
          AND t.taskStatus <> com.naleul.naleul.domain.task.enums.TaskStatus.DELETED
    """)
    List<Task> findAllActiveByGoalCategoryId(@Param("goalCategoryId") Long goalCategoryId);


    // 승인 취소 시 — 해당 참여자의 Task만 삭제 (지난번에 추가한 것)
    @Modifying
    @Query("DELETE FROM Task t WHERE t.missionParticipant.missionParticipantId = :participantId")
    void deleteByMissionParticipant_MissionParticipantId(@Param("participantId") Long participantId);

    // ✅ 신규 — 미션 삭제 시, 그 미션에 딸린 모든 참여자의 Task를 한 번에 삭제
    @Modifying
    @Query("DELETE FROM Task t WHERE t.missionParticipant.mission.missionId = :missionId")
    void deleteAllByMissionParticipantMissionMissionId(@Param("missionId") Long missionId);

    void deleteAllByUser_UserId(Long userId);

    long countByMissionParticipant_MissionParticipantIdAndTaskStatus(
            Long missionParticipantId, TaskStatus taskStatus);

    /** 목표별 마지막 Task 완료 시각 (완료 버튼을 누른 시각이 남아 있는 것만) — 목표 "멈춤" 계산용 */
    @Query("""
        SELECT t.goalCategory.goalCategoryId AS goalCategoryId, MAX(t.completedAt) AS lastAt
        FROM Task t
        WHERE t.user.userId = :userId
          AND t.goalCategory IS NOT NULL
          AND t.taskStatus = :status
          AND t.completedAt IS NOT NULL
        GROUP BY t.goalCategory.goalCategoryId
    """)
    List<GoalLastDoneProjection> findLastDoneByGoal(@Param("userId") Long userId,
                                                    @Param("status") TaskStatus status);

    /** 이 출처로 이미 가져온 외부 일정 id 중 ids 에 있는 것 (삭제한 Task 포함 — 지운 일정을 다시 가져오지 않게) */
    @Query("""
        SELECT t.externalEventId FROM Task t
        WHERE t.user.userId = :userId
          AND t.externalSource = :source
          AND t.externalEventId IN :ids
    """)
    List<String> findImportedExternalIds(@Param("userId") Long userId,
                                         @Param("source") ExternalCalendarSource source,
                                         @Param("ids") java.util.Collection<String> ids);

    /** 기간 안에 시작하는 시간 Task (외부 일정 가져올 때 "같은 이름 · 같은 시작 시각" 중복 확인용) */
    @Query("""
        SELECT t FROM Task t
        WHERE t.user.userId = :userId
          AND t.taskStatus <> com.naleul.naleul.domain.task.enums.TaskStatus.DELETED
          AND t.plannedStartAt >= :from AND t.plannedStartAt < :to
    """)
    List<Task> findTimedStartingBetween(@Param("userId") Long userId,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);

    /** 업무형 주제를 지울 때 그 주제의 Task 를 "주제 없음"으로 (V52) */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Task t SET t.workTopic = null WHERE t.workTopic.workTopicId = :topicId")
    int clearWorkTopic(@Param("topicId") Long topicId);
}
