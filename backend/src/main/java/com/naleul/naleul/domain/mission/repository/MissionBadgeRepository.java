package com.naleul.naleul.domain.mission.repository;

import com.naleul.naleul.domain.mission.entity.MissionBadge;
import com.naleul.naleul.domain.mission.enums.MissionBadgeType;
import com.naleul.naleul.domain.mission.enums.MissionCategoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MissionBadgeRepository extends JpaRepository<MissionBadge, Long> {

    boolean existsByMission_MissionIdAndUser_UserId(Long missionId, Long userId);

    // ✅ 변경 — "뱃지" 목록에서 퍼즐(ITEM_PUZZLE)은 제외 (퍼즐은 별도 API로 분리)
    @Query("""
        SELECT mb FROM MissionBadge mb
        WHERE mb.user.userId = :userId
        AND mb.badgeType <> com.naleul.naleul.domain.mission.enums.MissionBadgeType.ITEM_PUZZLE
        ORDER BY mb.acquiredAt DESC
        """)
    List<MissionBadge> findAllByUserId(@Param("userId") Long userId);

    // ✅ 변경 — 카테고리(대분류) 대신 카테고리 항목 기준으로 레벨업 대상 조회
    Optional<MissionBadge> findByUser_UserIdAndBadgeTypeAndCategoryItem(
            Long userId, MissionBadgeType badgeType, MissionCategoryItem categoryItem
    );

    boolean existsByUser_UserIdAndBadgeType(Long userId, MissionBadgeType badgeType);

    boolean existsByUser_UserIdAndBadgeTypeIn(Long userId, List<MissionBadgeType> badgeTypes);

    List<MissionBadge> findByUser_UserIdAndIsConfirmedFalse(Long userId);

    // ✅ 신규 — 뱃지 획득 모달(미확인 알림)에서도 퍼즐은 제외
    @Query("""
        SELECT mb FROM MissionBadge mb
        WHERE mb.user.userId = :userId
        AND mb.isConfirmed = false
        AND mb.badgeType <> com.naleul.naleul.domain.mission.enums.MissionBadgeType.ITEM_PUZZLE
        """)
    List<MissionBadge> findUnconfirmedBadgesByUserId(@Param("userId") Long userId);

    // ✅ 신규 — 퍼즐(카테고리 항목별) 전용 조회. categoryItem이 null이면 전체 항목, 있으면 그 항목만
    @Query("""
        SELECT mb FROM MissionBadge mb
        WHERE mb.user.userId = :userId
        AND mb.badgeType = com.naleul.naleul.domain.mission.enums.MissionBadgeType.ITEM_PUZZLE
        AND (:categoryItem IS NULL OR mb.categoryItem = :categoryItem)
        ORDER BY mb.acquiredAt DESC
        """)
    List<MissionBadge> findPuzzlesByUserId(
            @Param("userId") Long userId,
            @Param("categoryItem") MissionCategoryItem categoryItem
    );
}