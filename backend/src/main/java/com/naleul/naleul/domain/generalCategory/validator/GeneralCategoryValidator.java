package com.naleul.naleul.domain.generalCategory.validator;

import com.naleul.naleul.domain.generalCategory.dto.request.GeneralCategoryCreateRequest;
import com.naleul.naleul.domain.generalCategory.dto.request.GeneralCategoryUpdateRequest;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import com.naleul.naleul.global.policy.PlanLimits;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class GeneralCategoryValidator {

    private final GeneralCategoryRepository generalCategoryRepository;

    public void validateCreate(User user, GoalCategory goalCategory, GeneralCategoryCreateRequest request) {
        validateGeneralCategoryLimit(user, goalCategory);
        validateDateRange(request.getGeneralCategoryStartDate(), request.getGeneralCategoryEndDate());
    }

    public void validateUpdate(GeneralCategory generalCategory, GeneralCategoryUpdateRequest request) {
        LocalDate startDate = request.getGeneralCategoryStartDate() != null
                ? request.getGeneralCategoryStartDate()
                : generalCategory.getGeneralCategoryStartDate();
        LocalDate endDate = request.getGeneralCategoryEndDate() != null
                ? request.getGeneralCategoryEndDate()
                : generalCategory.getGeneralCategoryEndDate();

        validateDateRange(startDate, endDate);
    }

    private void validateGeneralCategoryLimit(User user, GoalCategory goalCategory) {
        if (user.isCurrentlyPremium()) {
            return;
        }

        long currentCount = generalCategoryRepository.countActiveByGoalCategoryId(goalCategory.getGoalCategoryId());
        if (currentCount >= PlanLimits.GENERAL_CATEGORY_FREE_LIMIT) {
            throw new CustomException(ErrorCode.GENERAL_CATEGORY_LIMIT_EXCEEDED);
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new CustomException(ErrorCode.GOAL_CATEGORY_INVALID_DATE);
        }
    }
}
