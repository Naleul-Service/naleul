package com.naleul.naleul.domain.pattern.calculator;

import java.time.LocalDate;
import java.util.List;

/**
 * 주차별 흐름 (명세 4 · 2-4)
 * @param rateStdDev 표본이 충분한 주들의 실행률 표준편차(%p) — 실행 스타일 "꾸준형/몰아치기형" 판정용
 */
public record WeeklyTrendStats(List<Point> points, Integer maxIndex, Double rateStdDev) {

    /** rate 가 null 이면 표본이 부족한 주 (선을 끊어 그림) */
    public record Point(int index, LocalDate weekStart, Integer rate, int samples) {
    }
}
