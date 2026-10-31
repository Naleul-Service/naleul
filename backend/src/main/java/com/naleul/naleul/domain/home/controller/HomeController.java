package com.naleul.naleul.domain.home.controller;

import com.naleul.naleul.domain.user.visit.VisitRecorder;
import com.naleul.naleul.domain.home.dto.response.HomeTodayResponse;
import com.naleul.naleul.domain.home.dto.response.ReflectionFeedResponse;
import com.naleul.naleul.domain.home.service.HomeService;
import com.naleul.naleul.domain.home.service.ReflectionFeedService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 홈 (웹 · 앱 첫 화면)
 * GET /home/today?date=  (date 없으면 오늘) 다가오는 마감(Task · 업무형 주제 · 목표형 종료일) + 진행 중인 목표별 오늘 (끝낸 일 · 못 한 일 · 오늘 회고)
 *
 * 오늘 일정 · 루틴 Task 는 GET /timetable, 생활 습관은 GET /habits, 회고 쓰기는 업무형 POST /retrospectives, 목표형 PUT /goal-categories/{id}/check-in 을 그대로 쓴다.
 * GET /home/reflections  회고 모아보기
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/home")
public class HomeController {

    private final HomeService homeService;
    private final ReflectionFeedService reflectionFeedService;
    /** V69: 방문한 날 (Aha-moment · 운영 대시보드 전용) */
    private final VisitRecorder visitRecorder;

    /**
     * @param date 보고 싶은 날 (yyyy-MM-dd, 없으면 오늘) — 홈에서 날짜 ‹ › 로 지난날 · 앞날을 볼 때.
     *             응답의 today 는 이 날짜이고, 마감 D-day 도 이 날 기준이다. 오늘 ±1년까지.
     */
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<HomeTodayResponse>> today(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        HomeTodayResponse body = homeService.today(userId, date);
        visitRecorder.record(userId, VisitRecorder.Source.HOME);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.HOME_FOUND, body));
    }

    /**
     * 회고 모아보기 — 목표형 "오늘 기록"(수치 · 기분 · 한 줄 회고)과 업무형 회고 글을 날짜별로.
     * @param before         이 날짜 전날까지 (없으면 오늘까지). 더 보기: 응답의 from 을 그대로 넣어요
     * @param days           몇 일치 (기본 30, 최대 92)
     * @param goalCategoryId 한 목표만 볼 때
     */
    @GetMapping("/reflections")
    public ResponseEntity<ApiResponse<ReflectionFeedResponse>> reflections(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate before,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Long goalCategoryId) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.REFLECTION_FEED_FOUND,
                reflectionFeedService.feed(userId, before, days, goalCategoryId)));
    }
}
