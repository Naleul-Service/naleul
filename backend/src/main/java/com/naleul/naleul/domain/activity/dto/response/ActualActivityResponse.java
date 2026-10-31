package com.naleul.naleul.domain.activity.dto.response;

import com.naleul.naleul.domain.activity.entity.ActualActivity;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;

import java.time.Duration;
import java.time.LocalDateTime;

public record ActualActivityResponse(
        Long activityId,
        String title,
        String emoji,
        LocalDateTime startAt,
        LocalDateTime endAt,
        long durationMinutes,
        Long goalCategoryId,
        String goalCategoryName,
        String goalColorCode,
        Long replacedTaskId,
        String replacedTaskName,
        String memo
) {
    public static ActualActivityResponse from(ActualActivity a) {
        GoalCategory g = a.getGoalCategory();
        return new ActualActivityResponse(
                a.getActualActivityId(), a.getTitle(), a.getEmoji(), a.getStartAt(), a.getEndAt(),
                Duration.between(a.getStartAt(), a.getEndAt()).toMinutes(),
                g != null ? g.getGoalCategoryId() : null,
                g != null ? g.getGoalCategoryName() : null,
                g != null && g.getUserColor() != null ? g.getUserColor().getColorCode() : null,
                a.getReplacedTaskId(), a.getReplacedTaskName(), a.getMemo());
    }
}
