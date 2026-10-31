package com.naleul.naleul.domain.chart;


import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GeneralCategoryRankItemDto {
    private Long generalCategoryId;
    private String generalCategoryName;
    private long achievedCount;
}