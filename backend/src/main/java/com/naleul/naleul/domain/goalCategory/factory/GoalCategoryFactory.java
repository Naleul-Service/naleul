package com.naleul.naleul.domain.goalCategory.factory;

import com.naleul.naleul.domain.goalCategory.dto.request.GoalCategoryCreateRequest;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import org.springframework.stereotype.Component;

@Component
public class GoalCategoryFactory {

    public GoalCategory create(User user, UserColor color, GoalCategoryCreateRequest request) {
        return GoalCategory.create(
                user,
                color,
                request.getGoalCategoryName(),
                request.getGoalCategoryStartDate(),
                request.getGoalCategoryEndDate(),
                request.getMotive()
        );
    }
}