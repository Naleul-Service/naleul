package com.naleul.naleul.domain.ideaBoard.repository;

import com.naleul.naleul.domain.ideaBoard.entity.IdeaBoard;
import com.naleul.naleul.domain.ideaBoard.enums.IdeaStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IdeaBoardRepository extends JpaRepository<IdeaBoard, Long> {

    // ⭐ 변경: 카테고리별 전체 조회
    List<IdeaBoard> findByUser_UserIdAndGoalCategory_GoalCategoryIdOrderByCreatedAtDesc(
            Long userId, Long goalCategoryId);

    // ⭐ 변경: 카테고리 + 상태 동시 필터
    List<IdeaBoard> findByUser_UserIdAndGoalCategory_GoalCategoryIdAndIdeaStatusOrderByCreatedAtDesc(
            Long userId, Long goalCategoryId, IdeaStatus ideaStatus);

    // 단건 조회 (본인 것인지 확인) — 변경 없음
    Optional<IdeaBoard> findByIdeaBoardIdAndUser_UserId(Long ideaBoardId, Long userId);

    // ⭐ 변경: 카테고리별 페이징
    Page<IdeaBoard> findByUser_UserIdAndGoalCategory_GoalCategoryIdOrderByCreatedAtDesc(
            Long userId, Long goalCategoryId, Pageable pageable);

    // 목표(GoalCategory) 하나당 아이디어보드 개수를 셈
    long countByUser_UserIdAndGoalCategory_GoalCategoryId(Long userId, Long goalCategoryId);
}