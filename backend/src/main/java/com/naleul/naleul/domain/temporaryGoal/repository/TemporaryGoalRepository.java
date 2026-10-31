package com.naleul.naleul.domain.temporaryGoal.repository;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.task.entity.Task;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 임시 목표 구체화 전용 쿼리 (명세 3 · BE-8).
 * 기존 Task/Routine/TaskActual Repository 를 덮어쓰지 않으려고 따로 모았다.
 */
public interface TemporaryGoalRepository extends Repository<Task, Long> {

    /** 임시 목표의 할 일 (AI 에게 "이미 있는 일"로 보여줌) */
    @Query("""
        SELECT t FROM Task t
        WHERE t.goalCategory.goalCategoryId = :goalId
          AND t.taskStatus <> com.naleul.naleul.domain.task.enums.TaskStatus.DELETED
        ORDER BY t.routineDate ASC, t.taskId ASC
    """)
    List<Task> findTasksOfGoal(@Param("goalId") Long goalId);

    /** 확정된 새 목표를 만든 세션의 출발 목표 (임시 목표에서 시작했을 때만 값이 있음) */
    @Query("""
        SELECT s.sourceGoalCategory FROM GoalCreationSession s
        WHERE s.confirmedGoalCategory.goalCategoryId = :confirmedGoalId
          AND s.sourceGoalCategory IS NOT NULL
    """)
    List<GoalCategory> findSourceOfConfirmedGoal(@Param("confirmedGoalId") Long confirmedGoalId);

    // ── 옮기기 (벌크 UPDATE: 엔티티를 하나씩 불러오지 않고 한 번에) ──

    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE Task t SET t.goalCategory = :goal, t.generalCategory = :general
        WHERE t.goalCategory.goalCategoryId = :fromGoalId
    """)
    int moveTasks(@Param("fromGoalId") Long fromGoalId, @Param("goal") GoalCategory goal,
                  @Param("general") GeneralCategory general);

    /** 실제 수행 기록도 같이 옮겨야 새 목표의 통계에 잡힌다 */
    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE TaskActual a SET a.goalCategory = :goal, a.generalCategory = :general
        WHERE a.goalCategory.goalCategoryId = :fromGoalId
    """)
    int moveActuals(@Param("fromGoalId") Long fromGoalId, @Param("goal") GoalCategory goal,
                    @Param("general") GeneralCategory general);

    /** 임시 목표에 루틴을 붙여 둔 경우 (기존 루틴 화면에서 만들 수 있음) */
    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE Routine r SET r.goalCategory = :goal, r.generalCategory = :general
        WHERE r.goalCategory.goalCategoryId = :fromGoalId
    """)
    int moveRoutines(@Param("fromGoalId") Long fromGoalId, @Param("goal") GoalCategory goal,
                     @Param("general") GeneralCategory general);
}
