package com.naleul.naleul.domain.goalCategory.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalCategoryAchievementRequest {

    @NotBlank(message = "달성 내용은 필수입니다.")
    private String achievement;

    private String achievementImageUrl;
}