package com.naleul.naleul.domain.chart.dto;

// domain/report/dto/AiFeedbackDto.java
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiFeedbackDto {
    private String title;    // "워커홀릭형 관점 주간 피드백"
    private String content;  // "커리어에 14시간, 이번 주 최고 몰입 기록이에요..."
}