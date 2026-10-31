package com.naleul.naleul.domain.chart.dto;

import com.naleul.naleul.domain.chart.GeneralCategoryRankItemDto;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GeneralCategoryRankingDto {
    private GeneralCategoryRankItemDto mostExecutedGeneralCategory;
    private GeneralCategoryRankItemDto leastExecutedGeneralCategory;
}