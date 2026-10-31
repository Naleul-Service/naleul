package com.naleul.naleul.domain.generalCategory.factory;

import com.naleul.naleul.domain.generalCategory.dto.request.GeneralCategoryCreateRequest;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import org.springframework.stereotype.Component;

@Component
public class GeneralCategoryFactory {

    public GeneralCategory create(User user, GoalCategory goalCategory, UserColor color,
                                  GeneralCategoryCreateRequest request) {
        return GeneralCategory.create(
                request.getGeneralCategoryName(),
                user,
                goalCategory,
                color,
                request.getGeneralCategoryStartDate(),
                request.getGeneralCategoryEndDate()
        );
    }
}