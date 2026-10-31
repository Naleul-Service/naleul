package com.naleul.naleul.domain.goalCategory.validator;

import com.naleul.naleul.domain.goalCategory.dto.request.GoalCategoryCreateRequest;
import com.naleul.naleul.domain.goalCategory.dto.request.GoalCategoryUpdateRequest;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import com.naleul.naleul.global.policy.PlanLimits;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class GoalCategoryValidator {

    private final GoalCategoryRepository goalCategoryRepository;

    public void validateCreate(User user, GoalCategoryCreateRequest request) {
        validateGoalCategoryLimit(user);
        validateDateRange(request.getGoalCategoryStartDate(), request.getGoalCategoryEndDate());
    }

    public void validateUpdate(GoalCategory goalCategory, GoalCategoryUpdateRequest request) {
        LocalDate mergedStartDate = request.getGoalCategoryStartDate() != null
                ? request.getGoalCategoryStartDate() : goalCategory.getGoalCategoryStartDate();
        LocalDate mergedEndDate = request.getGoalCategoryEndDate() != null
                ? request.getGoalCategoryEndDate() : goalCategory.getGoalCategoryEndDate();

        validateDateRange(mergedStartDate, mergedEndDate);
        // 수치 마감일은 목표 종료일과 따로지만, 시작일보다 앞설 수는 없다
        if (request.getMetricTargetDate() != null) validateDateRange(mergedStartDate, request.getMetricTargetDate());
    }

    /** Free 목표 개수 한도 (기록형 목표 생성 · 임시 목표 → 기록형 전환에서도 같이 쓴다) */
    public void validateCanAdd(User user) {
        validateGoalCategoryLimit(user);
    }

    private void validateGoalCategoryLimit(User user) {
        if (user.isCurrentlyPremium()) {
            return;
        }

        long currentCount = goalCategoryRepository.countActiveByUserId(user.getUserId());
        if (currentCount >= PlanLimits.GOAL_CATEGORY_FREE_LIMIT) {
            throw new CustomException(ErrorCode.GOAL_CATEGORY_LIMIT_EXCEEDED);
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new CustomException(ErrorCode.GOAL_CATEGORY_INVALID_DATE);
        }
    }
}