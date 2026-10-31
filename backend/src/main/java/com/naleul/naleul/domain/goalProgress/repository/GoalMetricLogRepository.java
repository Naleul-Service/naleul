package com.naleul.naleul.domain.goalProgress.repository;

import com.naleul.naleul.domain.goalProgress.entity.GoalMetricLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GoalMetricLogRepository extends JpaRepository<GoalMetricLog, Long> {

    @Query("""
        SELECT l FROM GoalMetricLog l
        WHERE l.goalCategory.goalCategoryId = :goalCategoryId
        ORDER BY l.logDate ASC
    """)
    List<GoalMetricLog> findAllOfGoal(@Param("goalCategoryId") Long goalCategoryId);

    @Query("""
        SELECT l FROM GoalMetricLog l
        WHERE l.goalCategory.goalCategoryId = :goalCategoryId
          AND l.logDate = :logDate
    """)
    Optional<GoalMetricLog> findOne(@Param("goalCategoryId") Long goalCategoryId,
                                    @Param("logDate") LocalDate logDate);

    /** 목표별 마지막 수치 기록일 — 목표 "멈춤" 계산용 */
    @Query("""
        SELECT l.goalCategory.goalCategoryId AS goalCategoryId, MAX(l.logDate) AS lastDate
        FROM GoalMetricLog l
        WHERE l.user.userId = :userId
        GROUP BY l.goalCategory.goalCategoryId
    """)
    List<GoalLastMetricLogProjection> findLastLogByGoal(@Param("userId") Long userId);

    /** 사용자의 [from, to] 수치 기록 — 홈 · 회고 모아보기 */
    @Query("""
        SELECT l FROM GoalMetricLog l
        JOIN FETCH l.goalCategory g
        WHERE l.user.userId = :userId
          AND l.logDate BETWEEN :from AND :to
          AND (:goalCategoryId IS NULL OR g.goalCategoryId = :goalCategoryId)
        ORDER BY l.logDate DESC
    """)
    List<GoalMetricLog> findAllOfUser(@Param("userId") Long userId, @Param("from") LocalDate from,
                                      @Param("to") LocalDate to, @Param("goalCategoryId") Long goalCategoryId);

    @Query("""
        SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END FROM GoalMetricLog l
        WHERE l.user.userId = :userId AND l.logDate < :before
          AND (:goalCategoryId IS NULL OR l.goalCategory.goalCategoryId = :goalCategoryId)
    """)
    boolean existsBefore(@Param("userId") Long userId, @Param("before") LocalDate before,
                         @Param("goalCategoryId") Long goalCategoryId);
}
