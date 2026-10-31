package com.naleul.naleul.domain.chart.dto;

import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class GoalCategoryStatisticsDto {
    private Long goalCategoryId;             // 상세 페이지 이동용
    private String goalCategoryName;
    private GoalCategoryStatus status;
    private String colorHex;

    private Integer generalCategoryCount;    // 세부 목표 개수
    private Integer routineCount;            // 루틴 개수

    private LocalDate startDate;
    private LocalDate endDate;
    private Long daysTogether;                // 함께한 기간(일)

    private Long totalMinutes;               // 투입한 시간(분) — 프론트에서 "47시간"으로 변환

    private Double routineAchievementRate;   // 루틴 실천률 (0~100)
    private Integer routineAchievedCount;
    private Integer routineTotalCount;

    private Double planAchievementRate;      // 계획 실천률 (0~100)
    private Integer planAchievedCount;
    private Integer planTotalCount;

    private String motive;                  // 진행중일 때만 값 있음
    private String achievement;             // 완료됐을 때만 값 있음
    private String achievementImageUrl;     // 완료됐을 때만 값 있음
}