package com.naleul.naleul.domain.goalCategory.repository;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GoalCategoryRepository extends JpaRepository<GoalCategory, Long> {

    /**
     * 특정 유저의 활성(삭제되지 않은) 목표 카테고리 개수를 셈.
     * Free 플랜 제한 체크용.
     * ⚠️ 임시 목표(Brain dump에서 AI가 만든 것)는 한도에서 제외 (명세 3 · 7장)
     * ⚠️ 기본 생활 습관 그릇(HABIT, V52)도 제외 — 모든 사용자에게 자동으로 생기는 목표라 한도를 차지하면 안 된다
     */
    @Query("SELECT COUNT(g) FROM GoalCategory g " +
            "WHERE g.user.userId = :userId " +
            "AND g.goalCategoryStatus <> com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus.DELETED " +
            "AND g.isTemporary = false " +
            "AND g.goalMode <> com.naleul.naleul.domain.goalCategory.enums.GoalMode.HABIT")
    long countActiveByUserId(@Param("userId") Long userId);

    /** 사용자의 임시 목표 개수 (최대 5개 제한용) */
    @Query("SELECT COUNT(g) FROM GoalCategory g " +
            "WHERE g.user.userId = :userId " +
            "AND g.goalCategoryStatus <> com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus.DELETED " +
            "AND g.isTemporary = true")
    long countTemporaryByUserId(@Param("userId") Long userId);

    @Query("""
    SELECT gc FROM GoalCategory gc
    LEFT JOIN FETCH gc.userColor
    LEFT JOIN FETCH gc.generalCategories genCat
    LEFT JOIN FETCH genCat.color
    WHERE gc.goalCategoryId = :goalCategoryId
    AND gc.goalCategoryStatus != 'DELETED'
    AND (genCat IS NULL OR genCat.generalCategoryStatus != 'DELETED')
""")
    Optional<GoalCategory> findByIdWithAll(@Param("goalCategoryId") Long goalCategoryId);

    // 유저 소유 확인 (DELETED 제외)
    @Query("""
    SELECT gc FROM GoalCategory gc
    WHERE gc.goalCategoryId = :id
    AND gc.user = :user
    AND gc.goalCategoryStatus != :deletedStatus
""")
    Optional<GoalCategory> findByGoalCategoryIdAndUser(
            @Param("id") Long id,
            @Param("user") User user,
            @Param("deletedStatus") GoalCategoryStatus deletedStatus
    );

    // ── 목록 조회 ──────────────────────────────────────────

    @Query("""
    SELECT gc FROM GoalCategory gc
    LEFT JOIN FETCH gc.userColor
    LEFT JOIN FETCH gc.generalCategories genCat
    LEFT JOIN FETCH genCat.color
    WHERE gc.user.userId = :userId
    AND gc.goalCategoryStatus != 'DELETED'
    AND gc.goalMode <> com.naleul.naleul.domain.goalCategory.enums.GoalMode.HABIT
    AND (genCat IS NULL OR genCat.generalCategoryStatus != 'DELETED')
""")
    List<GoalCategory> findAllByUserIdWithAll(@Param("userId") Long userId);
    // ⚠️ 위 목록은 기본 생활 습관 그릇(HABIT, V52)을 뺀다. 브레인덤프 목표 연결 · 통계 · 예전 앱의 목표 목록에
    //    체크용 습관 그릇이 섞이지 않게 하려는 것. 습관 그릇은 findHabitHome 으로 따로 꺼낸다.

    /** 사용자의 기본 생활 습관 그릇 (V52). 사용자당 1개 — 여러 개면 가장 먼저 만든 것 */
    @Query("""
    SELECT gc FROM GoalCategory gc
    LEFT JOIN FETCH gc.userColor
    WHERE gc.user.userId = :userId
    AND gc.goalCategoryStatus != 'DELETED'
    AND gc.goalMode = com.naleul.naleul.domain.goalCategory.enums.GoalMode.HABIT
    ORDER BY gc.goalCategoryId ASC
""")
    List<GoalCategory> findHabitHomes(@Param("userId") Long userId);

    // ── 색상 사용 여부 ─────────────────────────────────────

    @Query("""
        SELECT CASE WHEN COUNT(gc) > 0 THEN true ELSE false END
        FROM GoalCategory gc
        WHERE gc.userColor = :color
        AND gc.goalCategoryStatus != :deletedStatus
    """)
    boolean existsByUserColorAndNotDeleted(
            @Param("color") UserColor color,
            @Param("deletedStatus") GoalCategoryStatus deletedStatus
    );

    // ── 자동 상태 전환 (스케줄러용) ────────────────────────

    // 자동 시작 전환 대상 조회 — 시작일이 도래한 NOT_STARTED 목표
    @Query("""
    SELECT gc FROM GoalCategory gc
    WHERE gc.goalCategoryStatus = com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus.NOT_STARTED
      AND gc.goalCategoryStartDate <= :today
""")
    List<GoalCategory> findGoalsToStart(@Param("today") LocalDate today);

    // 자동 만료 대상 조회 — IN_PROGRESS 상태이면서 종료일이 지난 목표형(ACHIEVEMENT)만
    // (임시 목표는 종료일이 NULL이라 "NULL < today"가 참이 되지 않아 자연스럽게 제외됨)
    // 업무형(RECORD)은 V52 부터 마감일을 가질 수 있지만, 마감이 지나도 일이 남아 있을 수 있어 자동 완료하지 않는다.
    // 기본 생활 습관(HABIT)은 종료일이 없지만 혹시 생겨도 만료되지 않게 같이 막는다.
    @Query("""
    SELECT gc FROM GoalCategory gc
    WHERE gc.goalCategoryStatus = com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus.IN_PROGRESS
      AND gc.goalCategoryEndDate < :today
      AND gc.goalMode = com.naleul.naleul.domain.goalCategory.enums.GoalMode.ACHIEVEMENT
""")
    List<GoalCategory> findExpiredCategories(@Param("today") LocalDate today);

    void deleteAllByUser_UserId(Long userId);
}
