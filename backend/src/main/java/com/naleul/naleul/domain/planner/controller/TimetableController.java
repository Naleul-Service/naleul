package com.naleul.naleul.domain.planner.controller;

import com.naleul.naleul.domain.planner.dto.response.GoalTimeResponse;
import com.naleul.naleul.domain.planner.dto.response.MonthlyTimetableResponse;
import com.naleul.naleul.domain.planner.dto.response.TimetableResponse;
import com.naleul.naleul.domain.planner.service.GoalTimeQueryService;
import com.naleul.naleul.domain.planner.service.TimetableQueryService;
import com.naleul.naleul.domain.user.visit.VisitRecorder;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * GET /api/v1/timetable?startDate=&endDate=     일간(같은 날짜)·주간(최대 7일)
 * GET /api/v1/timetable/monthly?year=&month=    월간 요약
 * GET /api/v1/timetable/goal-time?startDate=&endDate=  목표별 실천 시간 (최대 42일)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/timetable")
public class TimetableController {

    private final TimetableQueryService timetableQueryService;
    private final VisitRecorder visitRecorder;
    private final GoalTimeQueryService goalTimeQueryService;

    @GetMapping
    public ResponseEntity<ApiResponse<TimetableResponse>> getTimetable(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        TimetableResponse body = timetableQueryService.getTimetable(userId, startDate, endDate);
        visitRecorder.record(userId, VisitRecorder.Source.TIMETABLE);   // V69: 방문한 날 (운영 대시보드 전용)
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.TIMETABLE_FOUND, body));
    }

    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<MonthlyTimetableResponse>> getMonthly(
            @AuthenticationPrincipal Long userId,
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.TIMETABLE_FOUND,
                timetableQueryService.getMonthly(userId, year, month)));
    }

    /** 목표별 실천 시간 — 캘린더 일간 · 3일 · 주간 · 월간의 작은 통계 (2026-10) */
    @GetMapping("/goal-time")
    public ResponseEntity<ApiResponse<GoalTimeResponse>> getGoalTime(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.TIMETABLE_FOUND,
                goalTimeQueryService.getGoalTime(userId, startDate, endDate)));
    }
}
