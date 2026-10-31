package com.naleul.naleul.domain.ideaBoard.service;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.ideaBoard.dto.request.IdeaBoardCreateRequest;
import com.naleul.naleul.domain.ideaBoard.dto.request.IdeaBoardUpdateRequest;
import com.naleul.naleul.domain.ideaBoard.dto.response.IdeaBoardResponse;
import com.naleul.naleul.domain.ideaBoard.entity.IdeaBoard;
import com.naleul.naleul.domain.ideaBoard.enums.IdeaStatus;
import com.naleul.naleul.domain.ideaBoard.repository.IdeaBoardRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.repository.UserRepository;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import com.naleul.naleul.global.policy.PlanLimits; // ✅ 추가
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IdeaBoardService {

    private final IdeaBoardRepository ideaBoardRepository;
    private final UserFinder userFinder;
    private final CategoryFinder categoryFinder;

    @Transactional
    public IdeaBoardResponse create(IdeaBoardCreateRequest request, Long userId) {
        User user = userFinder.findUser(userId);
        GoalCategory goalCategory = categoryFinder.findGoalCategory(request.getGoalCategoryId(), user);

        validateIdeaBoardLimit(user, goalCategory);

        IdeaBoard ideaBoard = IdeaBoard.builder()
                .user(user)
                .goalCategory(goalCategory)
                .idea(request.getIdea())
                .imageUrl(request.getImageUrl())
                .ideaStatus(IdeaStatus.PENDING)
                .build();

        IdeaBoard saved = ideaBoardRepository.save(ideaBoard);
        return IdeaBoardResponse.from(saved);
    }

    // ── READ ───────────────────────────────────────────────
    // (이하 조회 관련 메서드들은 전부 기존과 동일, 변경 없음)

    public List<IdeaBoardResponse> getMyIdeas(Long userId, Long goalCategoryId) {
        userFinder.validateUserExists(userId);
        return ideaBoardRepository
                .findByUser_UserIdAndGoalCategory_GoalCategoryIdOrderByCreatedAtDesc(userId, goalCategoryId)
                .stream()
                .map(IdeaBoardResponse::from)
                .toList();
    }

    public List<IdeaBoardResponse> getMyIdeasByStatus(Long userId, Long goalCategoryId, IdeaStatus status) {
        userFinder.validateUserExists(userId);
        return ideaBoardRepository
                .findByUser_UserIdAndGoalCategory_GoalCategoryIdAndIdeaStatusOrderByCreatedAtDesc(
                        userId, goalCategoryId, status)
                .stream()
                .map(IdeaBoardResponse::from)
                .toList();
    }

    public Page<IdeaBoardResponse> getMyIdeasPaged(Long userId, Long goalCategoryId, Pageable pageable) {
        userFinder.validateUserExists(userId);
        return ideaBoardRepository
                .findByUser_UserIdAndGoalCategory_GoalCategoryIdOrderByCreatedAtDesc(userId, goalCategoryId, pageable)
                .map(IdeaBoardResponse::from);
    }

    // ── UPDATE ─────────────────────────────────────────────

    @Transactional
    public IdeaBoardResponse update(Long ideaBoardId, Long userId, IdeaBoardUpdateRequest request) {
        IdeaBoard ideaBoard = ideaBoardRepository.findByIdeaBoardIdAndUser_UserId(ideaBoardId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.IDEA_BOARD_NOT_FOUND));

        ideaBoard.update(request.getIdea(), request.getImageUrl(), request.getIdeaStatus());

        return IdeaBoardResponse.from(ideaBoard);
    }

    // ── DELETE ─────────────────────────────────────────────

    @Transactional
    public void delete(Long ideaBoardId, Long userId) {
        IdeaBoard ideaBoard = ideaBoardRepository.findByIdeaBoardIdAndUser_UserId(ideaBoardId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.IDEA_BOARD_NOT_FOUND));

        ideaBoardRepository.delete(ideaBoard);
    }

    // ── private 헬퍼 ────────────────────────────────────────

    /**
     * Free 플랜 유저의 "목표(GoalCategory) 하나당 아이디어보드 생성 개수" 제한을 체크.
     * Pro 유저는 무제한이므로 바로 통과.
     *
     * 주의: 유저 전체 기준이 아니라, 이 특정 goalCategory 안에서만 개수를 셈.
     */
    private void validateIdeaBoardLimit(User user, GoalCategory goalCategory) {
        if (user.isCurrentlyPremium()) {
            return;
        }

        long currentCount = ideaBoardRepository.countByUser_UserIdAndGoalCategory_GoalCategoryId(
                user.getUserId(), goalCategory.getGoalCategoryId()
        );

        if (currentCount >= PlanLimits.IDEA_BOARD_FREE_LIMIT) {
            throw new CustomException(ErrorCode.IDEA_BOARD_LIMIT_EXCEEDED);
        }
    }
}