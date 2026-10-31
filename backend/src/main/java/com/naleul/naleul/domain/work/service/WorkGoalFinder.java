package com.naleul.naleul.domain.work.service;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 내 업무형(RECORD) 목표만 (V52). 없거나 남의 목표면 404, 다른 형태면 400 */
@Component
@RequiredArgsConstructor
public class WorkGoalFinder {

    private final CategoryFinder categoryFinder;

    public GoalCategory find(Long goalCategoryId, User user) {
        GoalCategory goal = categoryFinder.findGoalCategory(goalCategoryId, user);
        if (!goal.isRecord()) throw new CustomException(ErrorCode.WORK_GOAL_ONLY);
        return goal;
    }
}
