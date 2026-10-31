package com.naleul.naleul.domain.dreamBoard.service;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.dreamBoard.dto.request.DreamBoardCreateRequest;
import com.naleul.naleul.domain.dreamBoard.dto.response.DreamBoardResponse;
import com.naleul.naleul.domain.dreamBoard.entity.DreamBoard;
import com.naleul.naleul.domain.dreamBoard.repository.DreamBoardRepository;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import com.naleul.naleul.global.policy.PlanLimits;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DreamBoardService {

    private final DreamBoardRepository dreamBoardRepository;
    private final UserFinder userFinder;
    private final CategoryFinder categoryFinder;

    @Transactional
    public DreamBoardResponse create(DreamBoardCreateRequest request, Long userId) {
        User user = userFinder.findUser(userId);
        GoalCategory goalCategory = categoryFinder.findGoalCategory(request.getGoalCategoryId(), user);

        validateDreamBoardLimit(user, goalCategory);

        DreamBoard dreamBoard = DreamBoard.builder()
                .user(user)
                .goalCategory(goalCategory)
                .imageUrl(request.getImageUrl())
                .build();

        DreamBoard saved = dreamBoardRepository.save(dreamBoard);
        return DreamBoardResponse.from(saved);
    }

    public List<DreamBoardResponse> getMyDreamBoards(Long userId, Long goalCategoryId) {
        userFinder.validateUserExists(userId);
        return dreamBoardRepository
                .findByUser_UserIdAndGoalCategory_GoalCategoryIdOrderByCreatedAtDesc(userId, goalCategoryId)
                .stream()
                .map(DreamBoardResponse::from)
                .toList();
    }

    @Transactional
    public DreamBoardResponse updateImageUrl(Long dreamBoardId, Long userId, String imageUrl) {
        DreamBoard dreamBoard = dreamBoardRepository.findByDreamBoardIdAndUser_UserId(dreamBoardId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.DREAM_BOARD_NOT_FOUND));

        dreamBoard.updateImageUrl(imageUrl);
        return DreamBoardResponse.from(dreamBoard);
    }

    @Transactional
    public void delete(Long dreamBoardId, Long userId) {
        DreamBoard dreamBoard = dreamBoardRepository.findByDreamBoardIdAndUser_UserId(dreamBoardId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.DREAM_BOARD_NOT_FOUND));

        dreamBoardRepository.delete(dreamBoard);
    }

    // ── private 헬퍼 ────────────────────────────────────────

    /**
     * Free 플랜 유저의 "목표(GoalCategory) 하나당 드림보드 생성 개수" 제한을 체크.
     * Pro 유저는 무제한이므로 바로 통과.
     */
    private void validateDreamBoardLimit(User user, GoalCategory goalCategory) {
        if (user.isCurrentlyPremium()) {
            return;
        }

        long currentCount = dreamBoardRepository.countByUser_UserIdAndGoalCategory_GoalCategoryId(
                user.getUserId(), goalCategory.getGoalCategoryId()
        );

        if (currentCount >= PlanLimits.DREAM_BOARD_FREE_LIMIT) {
            throw new CustomException(ErrorCode.DREAM_BOARD_LIMIT_EXCEEDED);
        }
    }
}