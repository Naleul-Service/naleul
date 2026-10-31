package com.naleul.naleul.domain.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/v1/admin/dashboard/ai-interest — AI 관심도 (운영진 전용, V70).
 *
 * 시도 = 써 보려고 한 횟수 (한도에 막힌 것 포함) = 완료 + 중단 + 진행 중.
 * 플랜: 기능 표는 시도 당시 플랜(예전 기록은 지금 플랜), 맨 위 요약(활동 사용자 대비)은 지금 플랜.
 * 비율(%)은 분모가 0이면 null.
 */
public record AiInterestResponse(
        Period period,
        List<PlanSummary> plans,
        List<Feature> features,
        List<Drop> drops,
        List<Limit> limits,
        LocalDateTime limitTrackingSince
) {

    public record Period(LocalDate from, LocalDate to, int days, LocalDateTime generatedAt) {
    }

    /**
     * @param activeUsers    기간 안에 활동한 사용자 (방문 · Task 완료 · 실제 한 일 · AI 시도)
     * @param triedUsers     AI 기능을 하나라도 써 보려 한 사람
     * @param completedUsers AI 기능을 하나라도 끝까지 쓴 사람
     * @param avgFeatures    써 본 사람 1명이 시도한 기능 수 평균 (소수 1자리)
     */
    public record PlanSummary(String plan, long activeUsers, long triedUsers, Integer triedRate, long completedUsers,
                              Integer completedRate, Double avgFeatures) {
    }

    public record Feature(String key, String label, String page, String completeLabel, PlanStat free, PlanStat pro,
                          String note) {
    }

    /**
     * @param triedRate   써 본 사람 ÷ 같은 플랜 활동 사용자
     * @param repeatUsers 2번 이상 끝까지 쓴 사람
     */
    public record PlanStat(long users, Integer triedRate, long attempts, long completed, Integer completedRate,
                           long dropped, Integer dropRate, long inProgress, long completedUsers, long repeatUsers) {
    }

    /** 중단 한 줄 = 기능 × 화면 × 단계 (× 어떻게 끝났는지) */
    public record Drop(String feature, String featureLabel, String page, String step, String detail, DropStat free,
                       DropStat pro) {
    }

    /**
     * @param ofDrops    이 기능 중단 중 몇 %
     * @param ofAttempts 이 기능 시도 중 몇 %
     */
    public record DropStat(long count, Integer ofDrops, Integer ofAttempts) {
    }

    public record Limit(String feature, String featureLabel, String reason, String reasonLabel, long free, long freeUsers,
                        long pro, long proUsers) {
    }
}
