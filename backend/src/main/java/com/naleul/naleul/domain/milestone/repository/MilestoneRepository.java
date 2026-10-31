package com.naleul.naleul.domain.milestone.repository;

import com.naleul.naleul.domain.milestone.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    List<Milestone> findAllByGoalCategory_GoalCategoryIdOrderBySortOrderAsc(Long goalCategoryId);

    /** 내 목표의 마일스톤 (삭제된 목표 제외) */
    @Query("""
        SELECT m FROM Milestone m
        JOIN FETCH m.goalCategory g
        WHERE m.milestoneId = :milestoneId
          AND g.user.userId = :userId
          AND g.goalCategoryStatus <> com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus.DELETED
    """)
    Optional<Milestone> findOwned(@Param("milestoneId") Long milestoneId, @Param("userId") Long userId);

    /** 마일스톤을 지우기 전에 연결된 Task 의 연결을 끊는다 (task.milestone_id FK 에 ON DELETE 가 없음) */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("UPDATE Task t SET t.milestone = null WHERE t.milestone = :milestone")
    int detachTasks(@Param("milestone") Milestone milestone);
}
