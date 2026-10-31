package com.naleul.naleul.domain.generalCategory.repository;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalCategory.repository.projection.GoalCountProjection;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GeneralCategoryRepository extends JpaRepository<GeneralCategory, Long> {

    // ✅ 새로 추가 — 특정 GoalCategory(목표) 하나당 GeneralCategory 개수를 셈
    // ⚠️ "기타 할 일"(is_default_bucket)은 Free 세부 목표 한도에서 제외 (명세 3)
    @Query("SELECT COUNT(g) FROM GeneralCategory g " +
            "WHERE g.goalCategory.goalCategoryId = :goalCategoryId " +
            "AND g.generalCategoryStatus <> com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus.DELETED " +
            "AND g.isDefaultBucket = false")
    long countActiveByGoalCategoryId(@Param("goalCategoryId") Long goalCategoryId);

    // 목표의 "기타 할 일" 세부 목표 (없으면 Brain dump 확정 시 만든다)
    @Query("""
    SELECT g FROM GeneralCategory g
    WHERE g.goalCategory.goalCategoryId = :goalCategoryId
      AND g.isDefaultBucket = true
      AND g.generalCategoryStatus <> com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus.DELETED
""")
    List<GeneralCategory> findDefaultBuckets(@Param("goalCategoryId") Long goalCategoryId);

    // 유저의 전체 generalCategory 조회 (DELETED 제외)
    @Query("""
    SELECT DISTINCT gc FROM GeneralCategory gc
    LEFT JOIN FETCH gc.routines r
    WHERE gc.user = :user
      AND gc.generalCategoryStatus != 'DELETED'
""")
    List<GeneralCategory> findAllByUser(@Param("user") User user);

    @Query("""
    SELECT DISTINCT gc FROM GeneralCategory gc
    LEFT JOIN FETCH gc.goalCategory
    LEFT JOIN FETCH gc.color
    LEFT JOIN FETCH gc.routines
    WHERE gc.generalCategoryId = :id
""")
    Optional<GeneralCategory> findByIdWithAll(@Param("id") Long id);

    // 유저 소유 확인 (DELETED 제외)
    @Query("""
        SELECT g FROM GeneralCategory g
        WHERE g.generalCategoryId = :id
        AND g.user = :user
        AND g.generalCategoryStatus != :deletedStatus
    """)
    Optional<GeneralCategory> findByGeneralCategoryIdAndUser(
            @Param("id") Long id,
            @Param("user") User user,
            @Param("deletedStatus") GoalCategoryStatus deletedStatus
    );

    @Query("""
    SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END
    FROM GeneralCategory g
    WHERE g.color = :color
""")
    boolean existsByColor(@Param("color") UserColor color);

    // 유저의 goalCategory별 활성 세부목표 개수를 한번에 GROUP BY로 조회 (N+1 방지)
    @Query("""
        SELECT g.goalCategory.goalCategoryId AS goalCategoryId, COUNT(g) AS count
        FROM GeneralCategory g
        WHERE g.goalCategory.user.userId = :userId
          AND g.generalCategoryStatus <> com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus.DELETED
        GROUP BY g.goalCategory.goalCategoryId
    """)
    List<GoalCountProjection> countActiveGroupedByGoalCategory(@Param("userId") Long userId);


    // 자동 시작 대상 조회 — NOT_STARTED 상태 중 시작일이 도래한 것
    @Query("""
    SELECT gc FROM GeneralCategory gc
    WHERE gc.generalCategoryStatus = com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus.NOT_STARTED
      AND gc.generalCategoryStartDate <= :today
""")
    List<GeneralCategory> findGeneralCategoriesToStart(@Param("today") LocalDate today);

    // GoalCategory 하나에 속한 활성(삭제 안 된) GeneralCategory 전체 조회
    @Query("""
    SELECT gc FROM GeneralCategory gc
    WHERE gc.goalCategory.goalCategoryId = :goalCategoryId
      AND gc.generalCategoryStatus <> 'DELETED'
""")
    List<GeneralCategory> findAllActiveByGoalCategoryId(@Param("goalCategoryId") Long goalCategoryId);

    // 자동 만료 대상 조회 — DELETED, COMPLETED를 제외한 모든 세부목표 중 종료일이 지난 것
    @Query("""
    SELECT gc FROM GeneralCategory gc
    WHERE gc.generalCategoryStatus <> com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus.DELETED
      AND gc.generalCategoryStatus <> com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus.COMPLETED
      AND gc.generalCategoryEndDate < :today
""")
    List<GeneralCategory> findExpiredCategories(@Param("today") LocalDate today);

    void deleteAllByGoalCategory_User_UserId(Long userId);

    // 유저 소유 확인 (DELETED 제외) - 목록 버전
    @Query("""
    SELECT g FROM GeneralCategory g
    WHERE g.generalCategoryId IN :ids
    AND g.user = :user
    AND g.generalCategoryStatus != :deletedStatus
""")
    List<GeneralCategory> findAllByGeneralCategoryIdInAndUser(
            @Param("ids") List<Long> ids,
            @Param("user") User user,
            @Param("deletedStatus") GoalCategoryStatus deletedStatus
    );
}
