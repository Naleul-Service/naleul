package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChartResponseDto {
    private Long totalMinutes;          // 전체 기준값
    private List<ChartSliceDto> slices;
}