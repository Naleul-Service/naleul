package com.naleul.naleul.domain.planner.engine;

import com.naleul.naleul.domain.task.enums.TaskKind;
import com.naleul.naleul.domain.task.enums.TaskPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ImportanceAndEfficiencyTest {

    private static final LocalDate D = LocalDate.of(2026, 11, 18);

    @Test
    @DisplayName("중요도: 우선순위 + 마감 + 약속 + 이월 + 마일스톤, 등급 경계")
    void importance() {
        assertThat(ImportanceScorer.score(TaskPriority.C, null, TaskKind.NORMAL, 0, null, D).score()).isEqualTo(18);
        assertThat(ImportanceScorer.score(TaskPriority.C, null, TaskKind.NORMAL, 0, null, D).level()).isEqualTo(ImportanceLevel.LOW);

        Importance due = ImportanceScorer.score(TaskPriority.B, D.plusDays(1), TaskKind.NORMAL, 0, null, D);
        assertThat(due.score()).isEqualTo(54);
        assertThat(due.level()).isEqualTo(ImportanceLevel.MID);
        assertThat(due.cause()).isEqualTo(ReasonCode.DEADLINE_SOON);
        assertThat(due.causeValue()).isEqualTo(1);

        Importance overdue = ImportanceScorer.score(TaskPriority.A, D.minusDays(2), TaskKind.NORMAL, 0, null, D);
        assertThat(overdue.score()).isEqualTo(75);
        assertThat(overdue.cause()).isEqualTo(ReasonCode.OVERDUE);

        Importance carried = ImportanceScorer.score(TaskPriority.D, null, TaskKind.NORMAL, 5, null, D);
        assertThat(carried.score()).isEqualTo(12 + 24); // 이월 가산 최대 24
        assertThat(carried.cause()).isEqualTo(ReasonCode.CARRIED_OVER);

        // 점수는 LOW(34)라도 이월된 일은 최소 MID → 효율 낮은 시간대에 다시 넣지 않음
        Importance carriedLow = ImportanceScorer.score(TaskPriority.C, null, TaskKind.NORMAL, 2, null, D);
        assertThat(carriedLow.score()).isEqualTo(34);
        assertThat(carriedLow.level()).isEqualTo(ImportanceLevel.MID);

        Importance appt = ImportanceScorer.score(TaskPriority.A, D, TaskKind.APPOINTMENT, 0, null, D);
        assertThat(appt.score()).isEqualTo(100); // 30+40+50 → 최대 100
        assertThat(appt.cause()).isEqualTo(ReasonCode.APPOINTMENT);

        Importance ms = ImportanceScorer.score(TaskPriority.C, null, TaskKind.NORMAL, 0, D.plusDays(3), D);
        assertThat(ms.score()).isEqualTo(28);
        assertThat(ms.cause()).isEqualTo(ReasonCode.MILESTONE_SOON);
    }

    @Test
    @DisplayName("효율: 기록 14개 미만이면 기본 곡선(coldStart)")
    void coldStart() {
        List<HourlyEfficiencyCalculator.Sample> few = new ArrayList<>();
        for (int i = 0; i < 13; i++) few.add(new HourlyEfficiencyCalculator.Sample(D.atTime(9, 0), true));
        HourlyEfficiency e = HourlyEfficiencyCalculator.calculate(few);
        assertThat(e.coldStart()).isTrue();
        assertThat(e.at(D.atTime(10, 0))).isEqualTo(0.75);
    }

    @Test
    @DisplayName("효율: 표본이 적은 시간대는 전체 완료율 쪽으로 보정된다 (K=3)")
    void smoothing() {
        List<HourlyEfficiencyCalculator.Sample> s = new ArrayList<>();
        // 9시 10번 중 9번 완료, 21시 10번 중 2번 완료, 15시 1번 완료
        for (int i = 0; i < 10; i++) s.add(new HourlyEfficiencyCalculator.Sample(D.atTime(9, 0), i < 9));
        for (int i = 0; i < 10; i++) s.add(new HourlyEfficiencyCalculator.Sample(D.atTime(21, 0), i < 2));
        s.add(new HourlyEfficiencyCalculator.Sample(D.atTime(15, 0), true));
        HourlyEfficiency e = HourlyEfficiencyCalculator.calculate(s);

        double prior = 12.0 / 21;
        assertThat(e.coldStart()).isFalse();
        assertThat(e.at(D.atTime(9, 0))).isCloseTo((9 + 3 * prior) / 13, within(1e-9));
        assertThat(e.at(D.atTime(21, 0))).isCloseTo((2 + 3 * prior) / 13, within(1e-9));
        assertThat(e.at(D.atTime(15, 0))).isLessThan(0.7);   // 1/1 = 100% 가 아니라 보정됨
        assertThat(e.at(D.atTime(9, 0))).isGreaterThan(e.at(D.atTime(21, 0)));
    }
}
