package com.naleul.naleul.domain.goalCreation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI 목표 만들기 · AI 계획 초안 횟수 한도 (2026-10 BM 조정 — AI 비용 때문에 주 · 월 한도 추가).
 * 날짜는 모두 한국 시간: 하루 = 0시, 주 = 월요일 0시, 달 = 1일 0시에 다시 채워진다.
 *
 *               AI 목표 만들기(대화)      AI 계획 초안(다시 만들기 포함)
 *   Free        하루 1 · 주 2 · 월 8       하루 2 · 주 2 · 월 8
 *   Pro         하루 5 · 주 10 · 월 20     하루 5 · 주 10 · 월 20
 *
 * 초안 없이 대화만으로는 목표가 안 만들어지므로 대화 한도는 초안 한도에 맞췄다 (대화는 Haiku 라 싸고, 비용 대부분은 초안 · Sonnet).
 * 값은 yml goal.ai.quota.* 로 바꿀 수 있다. 예) goal.ai.quota.pro.draft-month: 30
 * Pro 판단은 GoalAiProperties.proLimitFor (Pro 구독 · 지정 사용자) 그대로.
 */
@Component
public class GoalAiQuota {

    /** 하루 · 주 · 월 한도 */
    public record Limits(int day, int week, int month) {
    }

    private final Limits freeSession;
    private final Limits proSession;
    private final Limits freeDraft;
    private final Limits proDraft;

    public GoalAiQuota(
            @Value("${goal.ai.quota.free.session-day:1}") int freeSessionDay,
            @Value("${goal.ai.quota.free.session-week:2}") int freeSessionWeek,
            @Value("${goal.ai.quota.free.session-month:8}") int freeSessionMonth,
            @Value("${goal.ai.quota.pro.session-day:5}") int proSessionDay,
            @Value("${goal.ai.quota.pro.session-week:10}") int proSessionWeek,
            @Value("${goal.ai.quota.pro.session-month:20}") int proSessionMonth,
            @Value("${goal.ai.quota.free.draft-day:2}") int freeDraftDay,
            @Value("${goal.ai.quota.free.draft-week:2}") int freeDraftWeek,
            @Value("${goal.ai.quota.free.draft-month:8}") int freeDraftMonth,
            @Value("${goal.ai.quota.pro.draft-day:5}") int proDraftDay,
            @Value("${goal.ai.quota.pro.draft-week:10}") int proDraftWeek,
            @Value("${goal.ai.quota.pro.draft-month:20}") int proDraftMonth) {
        this.freeSession = new Limits(freeSessionDay, freeSessionWeek, freeSessionMonth);
        this.proSession = new Limits(proSessionDay, proSessionWeek, proSessionMonth);
        this.freeDraft = new Limits(freeDraftDay, freeDraftWeek, freeDraftMonth);
        this.proDraft = new Limits(proDraftDay, proDraftWeek, proDraftMonth);
    }

    /** 기본값 (테스트용) */
    public static GoalAiQuota defaults() {
        return new GoalAiQuota(1, 2, 8, 5, 10, 20, 2, 2, 8, 5, 10, 20);
    }

    public Limits sessions(boolean pro) {
        return pro ? proSession : freeSession;
    }

    public Limits drafts(boolean pro) {
        return pro ? proDraft : freeDraft;
    }

    public enum Period { DAY, WEEK, MONTH }

    /**
     * 한도를 넘은 기간 — 하루 → 주 → 월 순서로 먼저 걸리는 것 (안 넘었으면 null).
     * @param usedSince 기간 시작 시각 → 그 뒤로 쓴 횟수
     */
    public static Period exceeded(Limits limits, java.util.function.Function<java.time.LocalDateTime, Long> usedSince,
                                  java.time.LocalDateTime dayStart, java.time.LocalDateTime weekStart,
                                  java.time.LocalDateTime monthStart) {
        if (usedSince.apply(dayStart) >= limits.day()) return Period.DAY;
        if (weekStart != null && usedSince.apply(weekStart) >= limits.week()) return Period.WEEK;
        if (monthStart != null && usedSince.apply(monthStart) >= limits.month()) return Period.MONTH;
        return null;
    }
}
