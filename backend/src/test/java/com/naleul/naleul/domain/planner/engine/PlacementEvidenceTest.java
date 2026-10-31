package com.naleul.naleul.domain.planner.engine;

import com.naleul.naleul.domain.planner.support.DateTimeRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.naleul.naleul.domain.planner.engine.PlacementFixtures.D;
import static org.assertj.core.api.Assertions.assertThat;

/** 내 기록 근거로 배치·설명하기 (목표별 시간 궁합 · 옮기기 습관 · "n개 중 m개") */
class PlacementEvidenceTest {

    /** 사용자 전체로는 아침(9시)이 강한 사람 */
    private static HourlyEfficiency morningPerson() {
        double[] a = new double[24];
        for (int h = 0; h < 24; h++) a[h] = 0.5;
        a[9] = 0.85;
        return new HourlyEfficiency(a, a.clone(), false, 60);
    }

    private static PlacementInput input(PlacementCandidate c, PatternEvidence ev) {
        return new PlacementInput(D, new DateTimeRange(D.atTime(7, 0), D.plusDays(1).atStartOfDay()), null,
                List.of(), List.of(), List.of(c), morningPerson(), 10, ev);
    }

    private static PlacementCandidate goalTask(long goalId) {
        return new PlacementCandidate(1L, goalId, 60, 60, new Importance(80, ImportanceLevel.of(80), null, 0), List.of());
    }

    @Test
    @DisplayName("목표별 시간 궁합: 전체로는 아침형이어도 '운동' 일을 저녁에 잘 지켜 왔으면 저녁에 놓고 근거를 말한다")
    void goalTimeFit() {
        int[] planned = new int[24], done = new int[24];
        int[] gp = new int[24], gd = new int[24];
        gp[19] = 6; gd[19] = 6;   // 운동은 19시에 6번 중 6번
        gp[9] = 4;  gd[9] = 0;    // 아침엔 4번 중 0번
        planned[9] = 20; done[9] = 17; planned[19] = 10; done[19] = 9;
        PatternEvidence ev = new PatternEvidence(planned, done,
                Map.of(5L, new PatternEvidence.GoalHours("운동", gp, gd)), new int[24], new int[24]);

        PlacementResult.Placed p = TimeBlockPlacementEngine.place(input(goalTask(5L), ev)).placed().get(0);

        assertThat(p.start().getHour()).isEqualTo(19);
        assertThat(p.reasonCodes()).contains(ReasonCode.GOAL_TIME_FIT);
        assertThat(p.reasonText()).contains("'운동' 일은 이 시간에 6번 중 6번 했어요.");
    }

    @Test
    @DisplayName("근거 문장은 보정 안 한 실제 횟수: '최근 4주 9시대에 계획한 일 20개 중 17개를 해냈어요'")
    void plainCounts() {
        int[] planned = new int[24], done = new int[24];
        planned[9] = 20; done[9] = 17; planned[15] = 20; done[15] = 8;
        PatternEvidence ev = new PatternEvidence(planned, done, Map.of(), new int[24], new int[24]);

        PlacementResult.Placed p = TimeBlockPlacementEngine.place(input(
                new PlacementCandidate(1L, null, 50, 50, new Importance(80, ImportanceLevel.of(80), null, 0), List.of()),
                ev)).placed().get(0);

        assertThat(p.start().getHour()).isEqualTo(9);
        assertThat(p.reasonText()).contains("최근 4주 9시대에 계획한 일 20개 중 17개를 해냈어요(평소 63%보다 높아요).");
    }

    @Test
    @DisplayName("옮기기 습관: 효율이 비슷하면 사용자가 자주 옮겨 오는 시간대를 고르고 그렇게 말한다")
    void moveHabit() {
        int[] in = new int[24], out = new int[24];
        in[14] = 6; out[10] = 6; // 늘 10시 → 14시로 옮김
        PatternEvidence ev = new PatternEvidence(new int[24], new int[24], Map.of(), in, out);
        double[] flat = new double[24];
        java.util.Arrays.fill(flat, 0.6);
        PlacementInput inp = new PlacementInput(D, new DateTimeRange(D.atTime(7, 0), D.plusDays(1).atStartOfDay()), null,
                List.of(), List.of(), List.of(new PlacementCandidate(1L, null, 50, 50,
                new Importance(80, ImportanceLevel.of(80), null, 0), List.of())),
                new HourlyEfficiency(flat, flat.clone(), false, 60), 10, ev);

        PlacementResult.Placed p = TimeBlockPlacementEngine.place(inp).placed().get(0);

        assertThat(p.start().getHour()).isEqualTo(14);
        assertThat(p.reasonCodes()).contains(ReasonCode.MOVE_HABIT);
        assertThat(p.reasonText()).contains("자주 옮기셔서");
    }
}
