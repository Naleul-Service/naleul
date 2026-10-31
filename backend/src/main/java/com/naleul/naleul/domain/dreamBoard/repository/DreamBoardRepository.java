package com.naleul.naleul.domain.dreamBoard.repository;

import com.naleul.naleul.domain.dreamBoard.entity.DreamBoard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DreamBoardRepository extends JpaRepository<DreamBoard, Long> {

    // ⭐ 변경: 카테고리별 조회로 교체
    List<DreamBoard> findByUser_UserIdAndGoalCategory_GoalCategoryIdOrderByCreatedAtDesc(
            Long userId, Long goalCategoryId);

    // 단건 조회 + 본인 소유 검증 (그대로 유지)
    Optional<DreamBoard> findByDreamBoardIdAndUser_UserId(Long dreamBoardId, Long userId);

    // 목표(GoalCategory) 하나당 드림보드 개수를 셈
    long countByUser_UserIdAndGoalCategory_GoalCategoryId(Long userId, Long goalCategoryId);
}