// domain/chart/dto/GoalCategoryHeaderDto.java
package com.naleul.naleul.domain.chart.dto;

import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class GoalCategoryHeaderDto {
    private Long goalCategoryId;
    private String goalCategoryName;
    private GoalCategoryStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private String motive;              // 진행중일 때만 값 존재
    private String achievement;         // 완료됐을 때만 값 존재
    private String achievementImageUrl; // 완료됐을 때만 값 존재
}