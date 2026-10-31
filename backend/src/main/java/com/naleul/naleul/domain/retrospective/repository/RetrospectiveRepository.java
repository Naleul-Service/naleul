// retrospective/repository/RetrospectiveRepository.java
package com.naleul.naleul.domain.retrospective.repository;

import com.naleul.naleul.domain.retrospective.entity.Retrospective;
import com.naleul.naleul.domain.retrospective.enums.ReviewType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface RetrospectiveRepository extends JpaRepository<Retrospective, Long> {

    @Query("""
        SELECT r FROM Retrospective r
        LEFT JOIN FETCH r.goalCategory
        LEFT JOIN FETCH r.generalCategory
        WHERE r.retrospectiveId = :id
        AND r.isDeleted = false
    """)
    Optional<Retrospective> findActiveById(@Param("id") Long id);

    @Query("""
        SELECT r FROM Retrospective r
        LEFT JOIN FETCH r.goalCategory
        LEFT JOIN FETCH r.generalCategory
        WHERE r.user.userId = :userId
        AND r.isDeleted = false
        AND (:reviewType IS NULL OR r.reviewType = :reviewType)
        AND (:startDate IS NULL OR r.reviewDate >= :startDate)
        AND (:endDate IS NULL OR r.reviewDate <= :endDate)
        AND (:goalCategoryId IS NULL OR r.goalCategory.goalCategoryId = :goalCategoryId)
        AND (:generalCategoryId IS NULL OR r.generalCategory.generalCategoryId = :generalCategoryId)
    """)
    Page<Retrospective> findAllByFilter(
            @Param("userId") Long userId,
            @Param("reviewType") ReviewType reviewType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("goalCategoryId") Long goalCategoryId,
            @Param("generalCategoryId") Long generalCategoryId,
            Pageable pageable
    );

    @Query("""
        SELECT r FROM Retrospective r
        LEFT JOIN FETCH r.goalCategory
        LEFT JOIN FETCH r.generalCategory
        WHERE r.retrospectiveId = :id
        AND r.user.userId = :userId
        AND r.isDeleted = false
    """)
    Optional<Retrospective> findActiveByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId
    );

    // ✅ 새로 추가 — GoalCategory(목표) 하나당 활성 회고 개수를 셈
    // (이전엔 유저 전체 기준이었지만, 이제 목표 단위로 계층이 바뀜)
    @Query("""
        SELECT COUNT(r) FROM Retrospective r
        WHERE r.goalCategory.goalCategoryId = :goalCategoryId
        AND r.isDeleted = false
    """)
    long countActiveByGoalCategoryId(@Param("goalCategoryId") Long goalCategoryId);
}