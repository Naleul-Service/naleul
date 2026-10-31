package com.naleul.naleul.domain.chart.service;

import com.naleul.naleul.domain.chart.dto.WeeklyReportDto;
import com.naleul.naleul.domain.chart.repository.WeeklyReportRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportOnDemandService {

    private final WeeklyReportRepository weeklyReportRepository;
    private final WeeklyReportGenerationService weeklyReportGenerationService;
    private final WeeklyReportQueryService weeklyReportQueryService;

    /**
     * 사용자가 "내 패턴 분석하기"를 눌렀을 때 호출.
     * - 오늘이 일요일이면 이번 주(월~오늘) 리포트를 즉시 생성해서 반환.
     * - 일요일이 아니면, 저번 주 리포트가 이미 있으면 예외를 던지고,
     *   없으면 저번 주 리포트를 새로 생성해서 반환.
     */
    @Transactional
    public WeeklyReportDto generateOnDemand(Long userId) {
        LocalDate today = LocalDate.now();

        if (today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            LocalDate weekStart = today.minusDays(6); // 이번 주 월요일
            LocalDate weekEnd = today;                 // 오늘(일요일)

            log.info("일요일 요청: 이번 주 리포트 생성/조회. userId={}, weekStart={}", userId, weekStart);
            weeklyReportGenerationService.generateReport(userId, weekStart, weekEnd);
            return weeklyReportQueryService.getReportDetail(userId, weekStart);
        }

        LocalDate lastWeekStart = today.with(DayOfWeek.MONDAY).minusWeeks(1);
        LocalDate lastWeekEnd = lastWeekStart.plusDays(6);

        boolean alreadyExists = weeklyReportRepository
                .existsByUserUserIdAndWeekStartDate(userId, lastWeekStart);

        if (alreadyExists) {
            log.info("저번 주 리포트가 이미 존재하여 생성 거부. userId={}, lastWeekStart={}", userId, lastWeekStart);
            throw new CustomException(ErrorCode.WEEKLY_REPORT_NOT_YET_AVAILABLE);
        }

        log.info("평일 요청, 저번 주 리포트 없음: 저번 주 리포트 생성. userId={}, lastWeekStart={}", userId, lastWeekStart);
        weeklyReportGenerationService.generateReport(userId, lastWeekStart, lastWeekEnd);
        return weeklyReportQueryService.getReportDetail(userId, lastWeekStart);
    }
}