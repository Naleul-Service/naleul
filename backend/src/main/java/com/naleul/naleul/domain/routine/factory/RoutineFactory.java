package com.naleul.naleul.domain.routine.factory;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.routine.dto.request.RoutineCreateRequest;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class RoutineFactory {
    public Routine create(
            User user,
            GoalCategory goalCategory,
            GeneralCategory generalCategory,
            RoutineCreateRequest request
    ) {
        return Routine.create(
                user,
                goalCategory,
                generalCategory,
                request.getRoutineName(),
                request.getRepeatStartTime(),
                request.getRepeatEndTime(),
                request.getRepeatStartDate(),
                request.getRepeatEndDate(),
                request.getRepeatDays(),
                request.isNotificationEnabled()
        );
    }
}
