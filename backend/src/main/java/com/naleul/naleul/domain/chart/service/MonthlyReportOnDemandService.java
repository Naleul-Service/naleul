package com.naleul.naleul.domain.chart.service;

import com.naleul.naleul.domain.chart.dto.MonthlyReportDto;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class MonthlyReportOnDemandService {

    private final MonthlyReportGenerationService monthlyReportGenerationService;
    private final MonthlyReportQueryService monthlyReportQueryService;

    /**
     * 사용자가 "월간 패턴 분석하기"를 눌렀을 때 호출.
     * 아직 처리되지 않은 4주 묶음이 있으면 그중 가장 오래된 것부터 생성.
     * (건너뛴 달이 있으면 자연히 그 달부터 순서대로 나옴)
     */
    @Transactional
    public MonthlyReportDto generateOnDemand(Long userId) {
        LocalDate periodStart = monthlyReportGenerationService.generateNextDueReport(userId);

        if (periodStart == null) {
            throw new CustomException(ErrorCode.MONTHLY_REPORT_NOT_YET_AVAILABLE);
        }

        return monthlyReportQueryService.getReportDetail(userId, periodStart);
    }
}