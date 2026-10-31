package com.naleul.naleul.domain.chart.dto;

import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChartSliceDto {
    private Long id;
    private String name;
    private String colorHex;        // UserColor에서 가져올 색상
    private Long totalMinutes;      // 실제 소요 시간 합계
    private Double percentage;      // 비율 (0~100)

    // ⚠️ 신규: "전체 목표" 도넛(findGoalCategoryStats)에서만 채워짐.
    //    generalCategory 도넛(findGeneralCategoryStats)에서는 status 개념이 없어서 null.
    private GoalCategoryStatus status;
}