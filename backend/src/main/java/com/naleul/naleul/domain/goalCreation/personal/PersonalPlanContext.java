package com.naleul.naleul.domain.goalCreation.personal;

/**
 * 계획 AI · 검증기 · "나에게 맞춘 점"이 함께 쓰는 개인 맞춤 정보.
 *
 * @param profile          최근 4주 실행 역량 (계산 실패면 empty)
 * @param intensity        계획 강도
 * @param weeklyCapMinutes 이 목표 루틴 주간 상한 (WeeklyCapPolicy)
 * @param achievers        같은 카테고리 달성자 집계 (표본 부족·카테고리 없음이면 null)
 */
public record PersonalPlanContext(
        CapacityProfile profile,
        PlanIntensity intensity,
        int weeklyCapMinutes,
        AchieverSummary achievers
) {
    public PersonalPlanContext {
        profile = profile != null ? profile : CapacityProfile.empty();
        intensity = intensity != null ? intensity : PlanIntensity.STEADY;
    }

    public static PersonalPlanContext of(CapacityProfile profile, PlanIntensity intensity, AchieverSummary achievers) {
        PlanIntensity i = intensity != null ? intensity : PlanIntensity.STEADY;
        return new PersonalPlanContext(profile, i, WeeklyCapPolicy.cap(profile, i), achievers);
    }
}
