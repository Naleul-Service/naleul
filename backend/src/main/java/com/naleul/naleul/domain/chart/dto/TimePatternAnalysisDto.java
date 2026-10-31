package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TimePatternAnalysisDto {
    private Integer bestHour;             // Task가 있던 시간대 중 완료율 최고
    private Double bestRate;
    private Double bestRatioOfTotal;      // "전체 작업의 38%가 이 시간대에" 계산용
    private Integer worstHour;
    private Double worstRate;
    private Double worstRatioOfTotal;
    private List<HourlyPatternDto> hourlyBreakdown; // 히트맵 렌더링용 (Task 있는 시간대만)
}