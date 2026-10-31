package com.naleul.naleul.domain.goalCreation.personal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 개인 루틴 주간 상한 */
class WeeklyCapPolicyTest {

    private static CapacityProfile profile(int tasks, Integer rate, int activeRoutineMinutes) {
        return new CapacityProfile(tasks, rate, 300, activeRoutineMinutes > 0 ? 3 : 0, activeRoutineMinutes,
                null, null, null, null, null);
    }

    @Test
    void 기록이_없으면_가볍게_240분() {
        assertThat(WeeklyCapPolicy.cap(CapacityProfile.empty(), PlanIntensity.STEADY)).isEqualTo(240);
        assertThat(WeeklyCapPolicy.cap(null, PlanIntensity.STEADY)).isEqualTo(240);
    }

    @Test
    void 실행률에_따라_출발점이_다르다() {
        assertThat(WeeklyCapPolicy.cap(profile(40, 85, 0), PlanIntensity.STEADY)).isEqualTo(420);
        assertThat(WeeklyCapPolicy.cap(profile(40, 70, 0), PlanIntensity.STEADY)).isEqualTo(300);
        assertThat(WeeklyCapPolicy.cap(profile(40, 40, 0), PlanIntensity.STEADY)).isEqualTo(210);
    }

    @Test
    void 표본이_적으면_기록이_없는_것과_같다() {
        assertThat(WeeklyCapPolicy.cap(profile(5, 90, 0), PlanIntensity.STEADY)).isEqualTo(240);
    }

    @Test
    void 이미_진행_중인_루틴이_많으면_줄이고_도전형은_늘린다() {
        assertThat(WeeklyCapPolicy.cap(profile(40, 85, 600), PlanIntensity.STEADY)).isEqualTo(300);   // 420 × 0.7 = 294 → 300
        assertThat(WeeklyCapPolicy.cap(profile(40, 85, 360), PlanIntensity.STEADY)).isEqualTo(360);   // 420 × 0.85 = 357 → 360
        assertThat(WeeklyCapPolicy.cap(profile(40, 85, 0), PlanIntensity.CHALLENGE)).isEqualTo(570);  // 420 × 1.35 = 567 → 570
        assertThat(WeeklyCapPolicy.cap(profile(40, 40, 900), PlanIntensity.STEADY)).isEqualTo(150);   // 210 × 0.7 = 147 → 150
    }
}
