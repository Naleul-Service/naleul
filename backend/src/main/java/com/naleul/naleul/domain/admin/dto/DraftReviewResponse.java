package com.naleul.naleul.domain.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/v1/admin/dashboard/draft-review — AI 초안 정확도 (운영진 전용, V68).
 * 확정 전: AI 최신 초안 ↔ 확정한 계획 (칸별, 자동 조정 칸 제외)
 * 확정 뒤: 첫 행동(루틴 체크 · Task 완료 · 실제 한 일 추가 · 수치 기록)부터 window(14 · 30)일.
 * 비율(%)은 분모가 0이면 null. 표본이 minSample 미만이면 화면에서 흐리게.
 */
public record DraftReviewResponse(
        Period period,
        Filters filters,
        int window,
        long sessions,
        Headline headline,
        Funnel funnel,
        List<PathRow> paths,
        QuickFix quickFix,
        List<EditBucket> buckets,
        List<ItemRow> items,
        List<DirectionRow> directions,
        List<WeeklyTimeRow> weeklyTime,
        NotUsed notUsed,
        List<String> promptVersions,
        int minSample
) {

    public record Period(LocalDate from, LocalDate to, int days, LocalDateTime generatedAt) {
    }

    public record Filters(String goalType, String intensity, String planningStyle, String promptVersion) {
    }

    /**
     * @param preservationRate 대표: 확정 계획에서 AI 값이 그대로 남은 칸 ÷ AI 가 만든 칸 (자동 조정 제외)
     * @param regenRate        실패 신호: "다시 생성"을 쓴 세션 ÷ 초안 화면에 들어온 세션
     * @param retentionRate    확정한 루틴 · Task 중 첫 행동부터 window 일 동안 안 고치고 안 지운 비율
     */
    public record Headline(Integer preservationRate, long keptFields, long aiFields,
                           Integer regenRate, long regenSessions, long enteredSessions,
                           Integer retentionRate, long retainedItems, long evaluatedItems) {
    }

    /** 초안 화면 들어온 세션 → 확정 · 그만둠 · 아직 머묾 (카테고리 필터만 적용) */
    public record Funnel(long entered, long confirmed, long exited, long pending, Integer exitRate) {
    }

    /**
     * @param path          REGEN · QUICK_FIX · MANUAL · AS_IS (가장 강한 신호 하나)
     * @param practiceRate  첫 행동부터 window 일 루틴 실천률 (해야 했던 루틴 중 체크)
     * @param retentionRate 확정 뒤 안 고치고 안 지운 루틴 · Task 비율
     */
    public record PathRow(String path, long sessions, Integer share, Integer practiceRate, long routinePlanned,
                          Integer retentionRate, long evaluatedItems) {
    }

    /**
     * @param successRate   말로 고친 뒤 다시 생성 없이 확정 ÷ 말로 고치기를 쓰고 끝난(확정 · 그만둠) 세션
     * @param thenRegen     말로 고치다가 다시 생성으로 넘어간 세션
     * @param baseEditCount 말로 고치기 직전에 손으로 고친 칸 수 합 (확정한 세션)
     */
    public record QuickFix(long sessions, long once, long twice, long threePlus, Integer successRate,
                           long succeeded, long finished, long thenRegen, long baseEditCount) {
    }

    /**
     * @param bucket NEAR 거의 그대로(보존율 90%+) · SOME 조금 수정(70~90%) · MANY 많이 수정(70% 미만)
     */
    public record EditBucket(String bucket, long sessions, Integer share, Integer practiceRate, long routinePlanned,
                             Integer achievementRate, long endedGoals) {
    }

    /**
     * @param aiItems      AI 가 만든 항목 수 (사용자 추가 제외)
     * @param topField     확정 전 가장 많이 고친 칸 (없으면 null)
     * @param retentionRate 확정 뒤 window 일 동안 안 고치고 안 지운 비율 (목표 · 세부 목표 · 마일스톤은 null)
     */
    public record ItemRow(String kind, String label, long aiItems, long edited, long deleted, long added,
                          Integer editRate, Integer deleteRate, Integer addRate, Integer keptRate,
                          String topField, String topFieldLabel, Integer topFieldRate,
                          Integer retentionRate, long evaluatedItems) {
    }

    /**
     * 자주 고치는 방향 (확정 전, 고친 칸만).
     * @param rate          이 방향으로 고친 칸 ÷ AI 가 만든 그 칸
     * @param avgDelta      평균 크기 (날짜=일, 시각 · 걸리는 시간=분, 요일=개수)
     * @param editedPractice 이렇게 고친 루틴의 첫 행동 후 window 일 실천률
     * @param keptPractice   같은 종류에서 AI 그대로 둔 항목의 실천률 (비교용)
     */
    public record DirectionRow(String kind, String field, String fieldLabel, String direction, long count,
                               Integer rate, BigDecimal avgDelta, Integer editedPractice, long editedPlanned,
                               Integer keptPractice, long keptPlanned) {
    }

    /** 루틴 시간 변화: 확정한 루틴의 일주일 총 시간 ÷ AI 초안 루틴의 일주일 총 시간 */
    public record WeeklyTimeRow(String intensity, long sessions, long draftMinutes, long confirmedMinutes, Integer rate) {
    }

    /** 확정 뒤 14일 안에 첫 행동이 없는 목표 (유지율 · 실천률에서 빠짐) */
    public record NotUsed(long count, long base, Integer rate) {
    }
}
