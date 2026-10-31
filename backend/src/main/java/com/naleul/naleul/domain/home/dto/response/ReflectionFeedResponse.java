package com.naleul.naleul.domain.home.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/v1/home/reflections — 회고 모아보기.
 * 목표형 "오늘 기록"(수치 + 기분 + 한 줄 회고)과 업무형 회고 글을 날짜별로 한 번에 보여 준다.
 *
 * from · to      : 이번에 담은 기간 (to 가 최근, 양 끝 포함)
 * entries        : 최근 날짜부터. 같은 날 안에서는 목표 id 순
 * hasMore        : from 보다 이전 기록이 더 있는지 — 있으면 before=from 으로 다시 부르면 돼요
 */
public record ReflectionFeedResponse(
        LocalDate from,
        LocalDate to,
        List<Entry> entries,
        boolean hasMore
) {

    public enum Kind { CHECK_IN, RETRO }

    /**
     * kind = CHECK_IN : 목표형 그날 기록. value(수치, 없을 수 있음) · mood · note
     * kind = RETRO    : 회고 글 (업무형 등). retrospectiveId · reviewType · note(=내용)
     */
    public record Entry(
            Kind kind,
            LocalDate date,
            Long goalCategoryId,
            String goalName,
            String goalEmoji,
            String colorCode,
            String goalMode,
            Long retrospectiveId,
            String reviewType,
            String mood,
            String note,
            BigDecimal value,
            String metricName,
            String unit
    ) {
    }
}
