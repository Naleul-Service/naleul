package com.naleul.naleul.domain.goalOutcome.repository;

import com.naleul.naleul.domain.goalCategory.kind.GoalSubType;
import com.naleul.naleul.domain.goalOutcome.entity.GoalOutcome;
import com.naleul.naleul.domain.goalOutcome.enums.OutcomePromptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GoalOutcomeRepository extends JpaRepository<GoalOutcome, Long> {

    Optional<GoalOutcome> findByGoalCategory_GoalCategoryId(Long goalCategoryId);

    List<GoalOutcome> findAllByGoalCategory_GoalCategoryIdIn(List<Long> goalCategoryIds);

    /** 결과 확인 모달 대기 목록 — 삭제된 목표 제외, 오래된 순 */
    @Query("""
        SELECT o FROM GoalOutcome o JOIN FETCH o.goalCategory g
        WHERE o.user.userId = :userId
          AND o.promptStatus = :status
          AND o.createdAt >= :since
          AND g.goalCategoryStatus <> com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus.DELETED
        ORDER BY o.createdAt ASC
    """)
    List<GoalOutcome> findPendingPrompts(@Param("userId") Long userId,
                                         @Param("status") OutcomePromptStatus status,
                                         @Param("since") LocalDateTime since);

    /** 같은 2단계 카테고리의 끝난 목표 (달성자 익명 집계) — 최근 since 이후 */
    @Query("""
        SELECT o FROM GoalOutcome o
        WHERE o.goalSubType = :subType
          AND o.updatedAt >= :since
    """)
    List<GoalOutcome> findForStats(@Param("subType") GoalSubType subType, @Param("since") LocalDateTime since);
}
