package com.naleul.naleul.domain.planner.engine;

import com.naleul.naleul.domain.planner.engine.PlacementInput.BusyTask;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Random;

import static com.naleul.naleul.domain.planner.engine.PlacementFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class TimeBlockPlacementEngineTest {

    private static HourlyEfficiency eveningPerson() {
        double[] a = new double[24];
        for (int h = 0; h < 24; h++) a[h] = 0.4;
        a[21] = 0.9; a[22] = 0.85; // 21~23시 완료율 높음
        a[15] = 0.2;               // 15시 완료율 낮음
        return new HourlyEfficiency(a, a.clone(), false, 60);
    }

    @Test
    @DisplayName("속성 테스트 1,000회: 고정 시간·다른 Task와 겹치지 않고, 간격·하루 범위·지금 이후·10분 단위를 지킨다")
    void neverOverlaps() {
        Random rnd = new Random(20261118L);
        for (int i = 0; i < 1000; i++) {
            PlacementInput in = PlacementFixtures.random(rnd);
            PlacementResult res = TimeBlockPlacementEngine.place(in);
            assertThat(PlacementFixtures.violation(in, res)).as("run %d", i).isNull();
        }
    }

    @Test
    @DisplayName("같은 입력이면 같은 결과 (결정적)")
    void deterministic() {
        PlacementInput in = PlacementFixtures.random(new Random(7));
        assertThat(TimeBlockPlacementEngine.place(in)).isEqualTo(TimeBlockPlacementEngine.place(in));
    }

    @Test
    @DisplayName("중요한 Task는 효율 높은 21시대, 가벼운 Task는 효율 낮은 15시대에 들어간다")
    void importanceMatchesEfficiency() {
        PlacementResult res = TimeBlockPlacementEngine.place(input(List.of(), List.of(),
                List.of(cand(1, 60, 80), cand(2, 30, 10)), eveningPerson()));

        PlacementResult.Placed high = res.placed().stream().filter(p -> p.taskId() == 1).findFirst().orElseThrow();
        PlacementResult.Placed low = res.placed().stream().filter(p -> p.taskId() == 2).findFirst().orElseThrow();
        assertThat(high.start().getHour()).isEqualTo(21);
        assertThat(high.reasonCodes()).contains(ReasonCode.HIGH_EFFICIENCY);
        assertThat(high.reasonText()).contains("21시대").endsWith("배치했어요.");
        assertThat(low.start().getHour()).isEqualTo(15);
        assertThat(low.reasonCodes()).contains(ReasonCode.LOW_LOAD_SLOT);
        // 일의 가치를 평가하는 말("가벼운 일") 대신 사실(마감 여유)만
        assertThat(low.reasonText()).startsWith("마감이 여유 있어서 15:").contains("잘 지키는 시간은 마감이 가까운 일에 먼저 남겨")
                .doesNotContain("가벼운");
    }

    @Test
    @DisplayName("고정 시간(점심)에는 넣지 않고, 끝나는 13:00에는 바로 붙여 넣을 수 있다")
    void respectsFixedBlocks() {
        // 07:00~12:00, 13:30~24:00 을 다른 고정 블록으로 막아 빈 시간은 12~13:30 중 점심 이후만 남김
        List<DateTimeRange> fixed = List.of(r(7, 0, 12, 0), r(12, 0, 13, 0), r(13, 30, 24, 0));
        PlacementResult res = TimeBlockPlacementEngine.place(input(fixed, List.of(), List.of(cand(1, 30, 50)),
                HourlyEfficiency.defaultCurve(0)));
        assertThat(res.placed()).hasSize(1);
        assertThat(res.placed().get(0).start().toLocalTime()).isEqualTo(LocalTime.of(13, 0));
        assertThat(res.placed().get(0).reasonCodes()).contains(ReasonCode.ONLY_SLOT);
    }

    @Test
    @DisplayName("잠긴 Task와는 10분 간격을 둔다")
    void keepsGapFromBusyTask() {
        List<DateTimeRange> fixed = List.of(r(7, 0, 19, 0), r(21, 0, 24, 0));
        List<BusyTask> busy = List.of(new BusyTask(r(19, 0, 20, 0), null));
        PlacementResult res = TimeBlockPlacementEngine.place(input(fixed, busy, List.of(cand(1, 50, 50)),
                HourlyEfficiency.defaultCurve(0)));
        // 20:00~21:00 중 간격 10분 → 20:10 시작, 50분이면 21:00 에 딱 맞음
        assertThat(res.placed().get(0).start().toLocalTime()).isEqualTo(LocalTime.of(20, 10));
    }

    @Test
    @DisplayName("빈 시간보다 긴 Task는 TOO_LONG, 자리가 다 찼으면 NO_SLOT")
    void unplacedCauses() {
        List<DateTimeRange> fixed = List.of(r(7, 0, 20, 0), r(21, 0, 24, 0)); // 20~21시만 빔
        PlacementResult res = TimeBlockPlacementEngine.place(input(fixed, List.of(),
                List.of(cand(1, 120, 90), cand(2, 50, 80), cand(3, 30, 10)), HourlyEfficiency.defaultCurve(0)));
        assertThat(res.unplaced()).extracting(PlacementResult.Unplaced::taskId).containsExactlyInAnyOrder(1L, 3L);
        assertThat(res.unplaced().stream().filter(u -> u.taskId() == 1).findFirst().orElseThrow().cause())
                .isEqualTo(PlacementResult.UnplacedCause.TOO_LONG);
        assertThat(res.unplaced().stream().filter(u -> u.taskId() == 3).findFirst().orElseThrow().cause())
                .isEqualTo(PlacementResult.UnplacedCause.NO_SLOT);
    }

    @Test
    @DisplayName("오늘이면 지금 이후(10분 올림)부터 배치")
    void notBefore() {
        PlacementInput base = input(List.of(), List.of(), List.of(cand(1, 30, 50)), HourlyEfficiency.defaultCurve(0));
        PlacementInput in = new PlacementInput(base.date(), base.dayRange(), D.atTime(22, 3), base.fixedBusy(),
                base.taskBusy(), base.candidates(), base.efficiency(), 10);
        PlacementResult res = TimeBlockPlacementEngine.place(in);
        assertThat(res.placed().get(0).start()).isAfterOrEqualTo(D.atTime(22, 10));
    }

    @Test
    @DisplayName("목표 선호 시간대(19~22시)가 있으면 그 안에 넣고 이유에 표시")
    void preferredWindow() {
        PlacementCandidate c = new PlacementCandidate(1L, 9L, 60, 60, new Importance(70, ImportanceLevel.HIGH, null, 0),
                List.of(new PlacementCandidate.Window(LocalTime.of(19, 0), LocalTime.of(22, 0))));
        PlacementResult res = TimeBlockPlacementEngine.place(input(List.of(), List.of(), List.of(c),
                HourlyEfficiency.defaultCurve(0)));
        PlacementResult.Placed p = res.placed().get(0);
        assertThat(p.start().toLocalTime()).isBetween(LocalTime.of(19, 0), LocalTime.of(21, 0));
        assertThat(p.reasonCodes()).contains(ReasonCode.PREFERRED_TIME);
        assertThat(p.reasonText()).contains("19:00~22:00");
    }

    @Test
    @DisplayName("마감 임박 이유가 문장 앞에 붙는다")
    void causeInReason() {
        PlacementCandidate c = new PlacementCandidate(1L, null, 60, 60,
                ImportanceScorer.score(com.naleul.naleul.domain.task.enums.TaskPriority.B, D.plusDays(1),
                        com.naleul.naleul.domain.task.enums.TaskKind.NORMAL, 0, null, D),
                List.of());
        PlacementResult.Placed p = TimeBlockPlacementEngine.place(input(List.of(), List.of(), List.of(c), eveningPerson()))
                .placed().get(0);
        assertThat(p.reasonText()).startsWith("마감이 1일 남아서 ");
        assertThat(p.reasonCodes()).startsWith(ReasonCode.DEADLINE_SOON);
    }

    @Test
    @DisplayName("좋은 시간대가 이미 차 있으면 '높은'이 아니라 '남은 빈 시간 중 가장 높은'이라고 말한다")
    void bestRemainingWording() {
        // 21~23시(효율 높음)를 고정 시간으로 막음
        List<DateTimeRange> fixed = List.of(r(21, 0, 23, 0));
        PlacementResult.Placed p = TimeBlockPlacementEngine.place(input(fixed, List.of(),
                List.of(cand(1, 60, 80)), eveningPerson())).placed().get(0);
        assertThat(p.reasonCodes()).contains(ReasonCode.BEST_REMAINING);
        assertThat(p.reasonText()).contains("남은 빈 시간 중 완료율이 가장 높은");
    }

    @Test
    @DisplayName("다른 이유가 있으면 '마감이 여유 있어서'를 붙이지 않는다")
    void noDoubleCause() {
        PlacementCandidate c = new PlacementCandidate(1L, null, 30, 30,
                new Importance(20, ImportanceLevel.LOW, ReasonCode.CARRIED_OVER, 1), List.of());
        PlacementResult.Placed p = TimeBlockPlacementEngine.place(input(List.of(), List.of(), List.of(c), eveningPerson()))
                .placed().get(0);
        assertThat(p.reasonText()).startsWith("어제 못 한 일이라 ").doesNotContain("마감이 여유 있어서").doesNotContain("가벼운");
    }

    // ── 2026-10: 나눠 넣기 · 같은 목표 묶기 · 쉬는 시간 ─────────────────

    @Test
    @DisplayName("연속된 빈 시간이 없으면 점심 앞뒤로 나눠 넣는다 — 4시간: 09~12시 + 13~14시")
    void splitsAroundLunch() {
        // 빈 시간: 09~12시, (점심 12~13시), 13~15시
        List<DateTimeRange> fixed = List.of(r(7, 0, 9, 0), r(12, 0, 13, 0), r(15, 0, 24, 0));
        PlacementResult res = TimeBlockPlacementEngine.place(input(fixed, List.of(), List.of(cand(1, 240, 70)),
                HourlyEfficiency.defaultCurve(0)));
        assertThat(res.unplaced()).isEmpty();
        PlacementResult.Placed p = res.placed().get(0);
        assertThat(p.split()).isTrue();
        assertThat(p.segments()).containsExactly(r(9, 0, 12, 0), r(13, 0, 14, 0));
        assertThat(p.start()).isEqualTo(D.atTime(9, 0));
        assertThat(p.end()).isEqualTo(D.atTime(14, 0));
        assertThat(p.reasonCodes()).contains(ReasonCode.SPLIT_BLOCK);
        assertThat(p.reasonText()).contains("09:00–12:00, 13:00–14:00 두 번에 나눠");
    }

    @Test
    @DisplayName("약속 일정은 나누지 않는다")
    void appointmentNotSplit() {
        List<DateTimeRange> fixed = List.of(r(7, 0, 9, 0), r(12, 0, 13, 0), r(15, 0, 24, 0));
        PlacementCandidate appt = new PlacementCandidate(1L, null, 240, 240,
                new Importance(70, ImportanceLevel.HIGH, null, 0), List.of(), false);
        PlacementResult res = TimeBlockPlacementEngine.place(input(fixed, List.of(), List.of(appt),
                HourlyEfficiency.defaultCurve(0)));
        assertThat(res.placed()).isEmpty();
        assertThat(res.unplaced().get(0).cause()).isEqualTo(PlacementResult.UnplacedCause.TOO_LONG);
    }

    @Test
    @DisplayName("마지막 조각이 30분보다 짧아지면 앞 조각을 줄여 30분을 맞춘다")
    void noTinyLastPiece() {
        // 빈 시간: 09~12시(3시간), 13~15시 / 3시간 10분 → 09:00~11:40 + 13:00~13:30 (자투리 10분 블록 대신)
        List<DateTimeRange> fixed = List.of(r(7, 0, 9, 0), r(12, 0, 13, 0), r(15, 0, 24, 0));
        PlacementResult.Placed p = TimeBlockPlacementEngine.place(input(fixed, List.of(), List.of(cand(1, 190, 70)),
                HourlyEfficiency.defaultCurve(0))).placed().get(0);
        assertThat(p.segments()).hasSize(2);
        assertThat(p.segments().get(1).minutes()).isGreaterThanOrEqualTo(30);
        assertThat(p.segments().stream().mapToLong(DateTimeRange::minutes).sum()).isEqualTo(190);
        assertThat(p.segments().get(0).end()).isEqualTo(D.atTime(12, 0));   // 첫 조각은 점심 직전까지 붙여서
    }

    @Test
    @DisplayName("10분 단위가 아닌 길이도 조각이 30분 밑으로 내려가지 않는다 (올림한 몫은 긴 조각에서 덜어냄)")
    void roundingLossFromLongestPiece() {
        List<DateTimeRange> fixed = List.of(r(7, 0, 9, 0), r(12, 0, 13, 0), r(15, 0, 24, 0));
        PlacementResult.Placed p = TimeBlockPlacementEngine.place(input(fixed, List.of(), List.of(cand(1, 185, 70)),
                HourlyEfficiency.defaultCurve(0))).placed().get(0);
        assertThat(p.segments()).hasSize(2);
        assertThat(p.segments()).allMatch(seg -> seg.minutes() >= 30);
        assertThat(p.segments().stream().mapToLong(DateTimeRange::minutes).sum()).isEqualTo(185);
        assertThat(p.segments().get(0).end()).isEqualTo(D.atTime(12, 0));
    }

    @Test
    @DisplayName("30분 조각을 만들 수 없으면 나누지 않는다 (40분을 30+10으로 쪼개지 않음)")
    void noSplitBelowMinPiece() {
        List<DateTimeRange> fixed = List.of(r(7, 0, 9, 0), r(9, 30, 10, 0), r(10, 30, 24, 0));
        PlacementResult res = TimeBlockPlacementEngine.place(input(fixed, List.of(), List.of(cand(1, 40, 70)),
                HourlyEfficiency.defaultCurve(0)));
        assertThat(res.placed()).isEmpty();
        assertThat(res.unplaced()).hasSize(1);
    }

    @Test
    @DisplayName("같은 목표의 일은 이어서 붙여 놓는다")
    void groupsSameGoal() {
        PlacementCandidate a = new PlacementCandidate(1L, 7L, 60, 60, new Importance(80, ImportanceLevel.HIGH, null, 0), List.of());
        PlacementCandidate b = new PlacementCandidate(2L, 7L, 60, 60, new Importance(50, ImportanceLevel.MID, null, 0), List.of());
        PlacementResult res = TimeBlockPlacementEngine.place(input(List.of(), List.of(), List.of(a, b), eveningPerson()));
        PlacementResult.Placed pa = res.placed().stream().filter(p -> p.taskId() == 1).findFirst().orElseThrow();
        PlacementResult.Placed pb = res.placed().stream().filter(p -> p.taskId() == 2).findFirst().orElseThrow();
        long rest = Math.min(Math.abs(java.time.Duration.between(pa.end(), pb.start()).toMinutes()),
                Math.abs(java.time.Duration.between(pb.end(), pa.start()).toMinutes()));
        assertThat(rest).isLessThanOrEqualTo(30);
        assertThat(pb.reasonCodes()).contains(ReasonCode.GOAL_GROUPED);
    }

    @Test
    @DisplayName("쉬는 시간이 30분 이내인 자리를 고른다 (블록 사이가 너무 벌어지지 않게)")
    void keepsRestShort() {
        // 효율이 평평한 사람: 이미 있는 10~11시 일정 뒤에 20분 쉬고 붙여 넣는다
        double[] flat = new double[24];
        java.util.Arrays.fill(flat, 0.5);
        HourlyEfficiency eff = new HourlyEfficiency(flat, flat.clone(), false, 60);
        List<BusyTask> busy = List.of(new BusyTask(r(10, 0, 11, 0), null));
        PlacementInput base = input(List.of(), busy, List.of(cand(1, 60, 80)), eff);
        PlacementInput in = new PlacementInput(base.date(), base.dayRange(), null, base.fixedBusy(), base.taskBusy(),
                base.candidates(), eff, 20);
        PlacementResult.Placed p = TimeBlockPlacementEngine.place(in).placed().get(0);
        boolean after = !p.start().isBefore(D.atTime(11, 20)) && !p.start().isAfter(D.atTime(11, 30));
        boolean before = !p.end().isAfter(D.atTime(9, 40)) && !p.end().isBefore(D.atTime(9, 30));
        assertThat(after || before).as("placed at %s", p.start()).isTrue();
    }
}
