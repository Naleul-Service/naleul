package com.naleul.naleul.domain.category.service;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CategoryFinder {

    private final GoalCategoryRepository goalCategoryRepository;
    private final GeneralCategoryRepository generalCategoryRepository;

    public GoalCategory findGoalCategory(Long goalCategoryId, User user) {
        if (goalCategoryId == null) return null;
        return goalCategoryRepository.findByGoalCategoryIdAndUser(goalCategoryId, user, GoalCategoryStatus.DELETED)
                .orElseThrow(() -> new CustomException(ErrorCode.GOAL_CATEGORY_NOT_FOUND));
    }

    public GeneralCategory findGeneralCategory(Long generalCategoryId, User user) {
        if (generalCategoryId == null) return null;
        return generalCategoryRepository.findByGeneralCategoryIdAndUser(generalCategoryId, user, GoalCategoryStatus.DELETED)
                .orElseThrow(() -> new CustomException(ErrorCode.GENERAL_CATEGORY_NOT_FOUND));
    }

    public List<GeneralCategory> findGeneralCategories(List<Long> generalCategoryIds, User user) {
        List<GeneralCategory> found = generalCategoryRepository
                .findAllByGeneralCategoryIdInAndUser(generalCategoryIds, user, GoalCategoryStatus.DELETED);

        if (found.size() != generalCategoryIds.size()) {
            throw new CustomException(ErrorCode.GENERAL_CATEGORY_NOT_FOUND);
        }

        return found;
    }
}