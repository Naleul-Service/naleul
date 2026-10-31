package com.naleul.naleul.domain.chart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naleul.naleul.domain.chart.dto.ChartResponseDto;
import com.naleul.naleul.domain.chart.dto.GoalWeeklyRankingDto;
import com.naleul.naleul.domain.chart.dto.WeekOverWeekDto;
import com.naleul.naleul.domain.chart.dto.*;
import com.naleul.naleul.domain.chart.entity.WeeklyReport;
import com.naleul.naleul.domain.chart.repository.WeeklyReportRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeeklyReportQueryService {

    private final WeeklyReportRepository weeklyReportRepository;
    private final ObjectMapper objectMapper;

    // ── 목록 조회 ("26.06.1주차, 2주차..." 형태) ──────────────

    public List<WeeklyReportSummary> getReportSummaries(Long userId) {
        return weeklyReportRepository.findAllByUserUserIdOrderByWeekStartDateDesc(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    private WeeklyReportSummary toSummary(WeeklyReport report) {
        LocalDate start = report.getWeekStartDate();
        int year = start.getYear() % 100;         // 2026 → 26
        int month = start.getMonthValue();
        int weekOfMonth = (start.getDayOfMonth() - 1) / 7 + 1;

        String label = String.format("%02d. %02d. %d주차 패턴 분석", year, month, weekOfMonth);

        return WeeklyReportSummary.builder()
                .weeklyReportId(report.getWeeklyReportId())
                .weekStartDate(start)
                .weekEndDate(report.getWeekEndDate())
                .year(year)
                .month(month)
                .weekOfMonth(weekOfMonth)
                .label(label)
                .build();
    }

    // ── 상세 조회 ("확인하기" 클릭 시) ────────────────────────

    public WeeklyReportDto getReportDetail(Long userId, LocalDate weekStartDate) {
        WeeklyReport report = weeklyReportRepository.findByUserUserIdAndWeekStartDate(userId, weekStartDate)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        AiFeedbackDto aiFeedback = AiFeedbackDto.builder()
                .title(report.getAiFeedbackTitle())
                .content(report.getAiFeedbackContent())
                .build();

        return WeeklyReportDto.builder()
                .weekStartDate(report.getWeekStartDate())
                .weekEndDate(report.getWeekEndDate())
                .patternType(report.getPatternTypeSnapshot())
                .aiFeedback(aiFeedback)
                .timePattern(fromJson(report.getTimePatternJson(), TimePatternAnalysisDto.class))
                .routineAchievement(fromJson(report.getRoutineAchievementJson(), RoutineAchievementWeeklyDto.class))
                .goalTimeDistribution(fromJson(report.getGoalTimeDistributionJson(), ChartResponseDto.class))
                .goalRanking(fromJson(report.getGoalRankingJson(), GoalWeeklyRankingDto.class))
                .weekOverWeek(fromJson(report.getWeekOverWeekJson(), WeekOverWeekDto.class))
                .build();
    }

    private <T> T fromJson(String json, Class<T> targetType) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, targetType);
        } catch (Exception e) {
            throw new RuntimeException("JSON 역직렬화 실패: " + targetType.getSimpleName(), e);
        }
    }
}