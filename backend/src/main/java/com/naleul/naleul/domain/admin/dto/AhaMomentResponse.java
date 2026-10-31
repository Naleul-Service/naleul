package com.naleul.naleul.domain.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/v1/admin/dashboard/aha — Aha-moment (운영진 전용, V69).
 * 기준: 가입 후 7일 안에(가입일 포함 7일) ① 목표 2개 생성 ② Task 3개 이상 완료(루틴 · 일반) ③ 서로 다른 3일 이상 방문.
 * 대상: 기간 안에 가입했고 가입 후 7일이 지난 사용자 (아직 7일이 안 지난 사람은 pending).
 * 비율(%)은 분모가 0이면 null.
 */
public record AhaMomentResponse(
        Period period,
        long signups,
        long eligible,
        long pending,
        Rate aha,
        List<Condition> conditions,
        List<Step> funnel,
        Retention retention,
        Distributions distributions,
        List<Cohort> cohorts,
        LocalDate visitTrackingSince,
        int minSample
) {

    public record Period(LocalDate from, LocalDate to, int days, LocalDateTime generatedAt) {
    }

    public record Rate(long users, Integer rate) {
    }

    /** @param key GOALS · TASKS · VISITS */
    public record Condition(String key, String label, long users, Integer rate) {
    }

    /** 순서대로 쌓은 단계: ① → ①+② → ①+②+③ */
    public record Step(String label, long users, Integer rate) {
    }

    /**
     * D14 리텐션 = 가입 14~20일째(가입일 = 0일째)에 한 번이라도 방문 · Task 완료 · 실제 한 일 추가를 한 비율.
     * 가입 후 21일이 지난 사람만 센다. gapPp = 도달 − 미도달 (%p)
     */
    public record Retention(long reachedBase, long reachedRetained, Integer reachedRate,
                            long notReachedBase, long notReachedRetained, Integer notReachedRate,
                            Integer gapPp, long overallBase, Integer overallRate) {
    }

    public record Bucket(String label, long users, Integer share) {
    }

    public record Distributions(List<Bucket> goals, List<Bucket> tasks, List<Bucket> visitDays) {
    }

    /** 가입 주(월요일 시작)별 */
    public record Cohort(LocalDate weekStart, long signups, long eligible, Integer goalsRate, Integer tasksRate,
                         Integer visitsRate, Integer ahaRate, Integer d14Reached, Integer d14NotReached) {
    }
}
