package com.naleul.naleul.domain.goalProgress.repository;

import com.naleul.naleul.domain.goalProgress.entity.GoalReflection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GoalReflectionRepository extends JpaRepository<GoalReflection, Long> {

    @Query("""
        SELECT r FROM GoalReflection r
        WHERE r.goalCategory.goalCategoryId = :goalCategoryId
          AND r.reflectDate = :date
    """)
    Optional<GoalReflection> findOne(@Param("goalCategoryId") Long goalCategoryId, @Param("date") LocalDate date);

    /** from 이후 회고, 최근 날짜부터 */
    @Query("""
        SELECT r FROM GoalReflection r
        WHERE r.goalCategory.goalCategoryId = :goalCategoryId
          AND r.reflectDate >= :from
        ORDER BY r.reflectDate DESC
    """)
    List<GoalReflection> findRecent(@Param("goalCategoryId") Long goalCategoryId, @Param("from") LocalDate from);

    /** 사용자의 [from, to] 회고 — 홈 · 회고 모아보기 (목표도 같이 읽어요) */
    @Query("""
        SELECT r FROM GoalReflection r
        JOIN FETCH r.goalCategory g
        WHERE r.user.userId = :userId
          AND r.reflectDate BETWEEN :from AND :to
          AND (:goalCategoryId IS NULL OR g.goalCategoryId = :goalCategoryId)
        ORDER BY r.reflectDate DESC
    """)
    List<GoalReflection> findAllOfUser(@Param("userId") Long userId, @Param("from") LocalDate from,
                                       @Param("to") LocalDate to, @Param("goalCategoryId") Long goalCategoryId);

    /** before 보다 이전 회고가 있는지 — 회고 모아보기 "더 보기" */
    @Query("""
        SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM GoalReflection r
        WHERE r.user.userId = :userId AND r.reflectDate < :before
          AND (:goalCategoryId IS NULL OR r.goalCategory.goalCategoryId = :goalCategoryId)
    """)
    boolean existsBefore(@Param("userId") Long userId, @Param("before") LocalDate before,
                         @Param("goalCategoryId") Long goalCategoryId);
}
