package com.naleul.naleul.domain.pattern.calculator;

import com.naleul.naleul.domain.pattern.entity.BrainDumpLog;
import com.naleul.naleul.domain.pattern.entity.TaskScheduleLog;
import com.naleul.naleul.domain.pattern.enums.DayGroup;
import com.naleul.naleul.domain.pattern.enums.HeatCellState;
import com.naleul.naleul.domain.pattern.enums.PatternPeriod;
import com.naleul.naleul.domain.pattern.enums.TaskScheduleAction;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.naleul.naleul.domain.pattern.calculator.PatternFixtures.END;
import static com.naleul.naleul.domain.pattern.calculator.PatternFixtures.START;
import static org.assertj.core.api.Assertions.assertThat;

/** 나의 패턴 계산기 단위 테스트 (명세 4 · 2장, 11장 인수 조건) */
class PatternCalculatorsTest {

    private final PatternFixtures f = new PatternFixtures();

    /** START(11-02 월) 부터 평일만 n 개 */
    private static List<LocalDate> weekdays(int n) {
        List<LocalDate> out = new java.util.ArrayList<>();
        for (LocalDate d = START; out.size() < n; d = d.plusDays(1)) {
            if (!DayGroup.isWeekend(d.getDayOfWeek())) out.add(d);
        }
        return out;
    }

    // ── 기간 ──────────────────────────────────────────────

    @Test
    void 최근4주는_어제까지_28일() {
        PatternRange r = PatternRange.of(PatternPeriod.RECENT_4W, LocalDate.of(2026, 11, 30), null);
        assertThat(r.start()).isEqualTo(START);
        assertThat(r.end()).isEqualTo(END);
        assertThat(r.days()).isEqualTo(28L);
    }

    @Test
    void 전체는_첫_기록부터_최대_365일() {
        LocalDate today = LocalDate.of(2026, 11, 30);
        assertThat(PatternRange.of(PatternPeriod.ALL, today, LocalDate.of(2026, 11, 20)).start())
                .isEqualTo(LocalDate.of(2026, 11, 20));
        PatternRange capped = PatternRange.of(PatternPeriod.ALL, today, LocalDate.of(2024, 1, 1));
        assertThat(capped.days()).isEqualTo(365L);
        assertThat(PatternRange.of(PatternPeriod.ALL, today, null).start()).isEqualTo(START);
    }

    // ── 요약 ──────────────────────────────────────────────

    @Test
    void 실행률은_이월_놓침을_분모에_더한다() {
        for (int i = 0; i < 10; i++) f.block(START.plusDays(i), 9, i < 8);
        assertThat(SummaryCalculator.calculate(f.set()).executionRate()).isEqualTo(80);

        f.carries.add(new CarryMissSample(99L, null, START, null));
        f.carries.add(new CarryMissSample(98L, null, START, START.atTime(10, 0)));
        assertThat(SummaryCalculator.calculate(f.set()).executionRate()).isEqualTo(67); // 8 / 12
    }

    @Test
    void 평가_대상이_10개_미만이면_실행률은_null() {
        for (int i = 0; i < 9; i++) f.block(START.plusDays(i), 9, true);
        SummaryStats s = SummaryCalculator.calculate(f.set());
        assertThat(s.executionRate()).isNull();
        assertThat(s.insufficient().contains("executionRate")).isTrue();
    }

    @Test
    void 연속_실행은_Task_없는_날을_건너뛴다() {
        for (int i = 0; i < 5; i++) f.block(START.plusDays(i), 9, true); // 11-02 ~ 06 실행
        f.block(START.plusDays(5), 9, false);                             // 11-07 놓침
        f.block(START.plusDays(6), 9, true);                              // 11-08
        f.block(START.plusDays(7), 9, true);                              // 11-09, 이후 Task 없음
        SummaryStats s = SummaryCalculator.calculate(f.set());
        assertThat(s.bestStreak()).isEqualTo(5);
        assertThat(s.currentStreak()).isEqualTo(2);
    }

    @Test
    void 첫_주_대비_변화() {
        for (int i = 0; i < 10; i++) f.block(START.plusDays(i % 7), 9 + i, i < 6);          // 1주: 60%
        for (int i = 0; i < 10; i++) f.block(START.plusDays(21 + i % 7), 9 + i, i < 9);     // 4주: 90%
        assertThat(SummaryCalculator.calculate(f.set()).firstWeekDelta()).isEqualTo(30);
    }

    @Test
    void 실제_시각이_없는_완료는_지연_0분으로_평균에_포함() {
        for (int i = 0; i < 5; i++) {
            LocalDateTime s = START.plusDays(i).atTime(8, 0);
            f.task().at(s).done(true).actual(s.plusMinutes(20), 30).add();
        }
        for (int i = 0; i < 5; i++) f.block(START.plusDays(i), 19, true); // 기록 없음 → 0분
        SummaryStats s = SummaryCalculator.calculate(f.set());
        assertThat(s.morningDelayMinutes()).isEqualTo(20);
        assertThat(s.eveningDelayMinutes()).isEqualTo(0);
        assertThat(s.avgStartDelayMinutes()).isEqualTo(10);
    }

    @Test
    void AI_배치_수정률은_그날_사용자가_옮긴_엔진_블록_비율() {
        LocalDate d = START.plusDays(1);
        LocalDateTime nightBefore = START.atTime(22, 0);
        for (long id = 1; id <= 5; id++) {
            f.logs.add(TaskScheduleLog.of(1L, id, null, TaskScheduleAction.ENGINE_PLACE,
                    null, null, null, d.atTime(9, 0), nightBefore));
        }
        f.logs.add(TaskScheduleLog.of(1L, 1L, null, TaskScheduleAction.USER_MOVE,
                null, d.atTime(9, 0), null, d.atTime(12, 0), d.atTime(10, 0)));
        // 다른 날짜의 이동은 세지 않음
        f.logs.add(TaskScheduleLog.of(1L, 2L, null, TaskScheduleAction.USER_MOVE,
                null, d.plusDays(1).atTime(9, 0), null, d.plusDays(1).atTime(12, 0), d.plusDays(1).atTime(10, 0)));

        SummaryStats s = SummaryCalculator.calculate(f.set());
        assertThat(s.aiPlacementEditRate()).isEqualTo(20);
        assertThat(s.aiPlacementKeptPerTen()).isEqualTo(8);
        assertThat(s.engineBlockCount()).isEqualTo(5);
    }

    // ── 히트맵 ────────────────────────────────────────────

    @Test
    void 히트맵_칸_상태() {
        for (int w = 0; w < 3; w++) f.block(START.plusWeeks(w), 19, true);  // 월 19시 3개
        for (int w = 0; w < 2; w++) f.block(START.plusWeeks(w), 20, true);  // 월 20시 2개 (실행률은 표본 부족)
        f.block(START.plusDays(1), 18, false);                               // 화 18시 놓침 → 한 게 없어 빈 칸
        f.custom("회사 업무", 9, 30, 18, 0, 31);                               // 월~금 09:30~18:00

        HeatmapStats h = HeatmapCalculator.calculate(f.set());
        HeatmapStats.Row mon = h.rows().get(0);
        HeatmapStats.Row tue = h.rows().get(1);
        HeatmapStats.Row sat = h.rows().get(5);
        assertThat(mon.dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);

        HeatmapStats.Cell mon19 = mon.cells().get(19 - HeatmapCalculator.FIRST_HOUR);
        assertThat(mon19.state()).isEqualTo(HeatCellState.VALUE);
        assertThat(mon19.count()).isEqualTo(3);
        assertThat(mon19.rate()).isEqualTo(100);
        assertThat(mon19.level()).isEqualTo(5);
        // 한 수로 칠해요: 2번 했으면 표본이 적어도 칸이 보여요 (가장 많이 한 칸 3 대비 → 4단계)
        HeatmapStats.Cell mon20 = mon.cells().get(20 - 6);
        assertThat(mon20.state()).isEqualTo(HeatCellState.VALUE);
        assertThat(mon20.count()).isEqualTo(2);
        assertThat(mon20.level()).isEqualTo(4);
        assertThat(mon20.rate()).isNull();
        assertThat(h.maxCount()).isEqualTo(3);
        assertThat(mon.cells().get(9 - 6).state()).isEqualTo(HeatCellState.FIXED);  // 30분 덮음
        assertThat(tue.cells().get(10 - 6).state()).isEqualTo(HeatCellState.FIXED);
        assertThat(tue.cells().get(18 - 6).state()).isEqualTo(HeatCellState.EMPTY);
        assertThat(sat.cells().get(10 - 6).state()).isEqualTo(HeatCellState.EMPTY);
        assertThat(h.fixedLabel()).isEqualTo("회사 업무");
        assertThat(h.hours()).hasSize(18);
    }

    @Test
    void 자정을_넘기는_패턴은_다음_요일_새벽을_덮는다() {
        LifePatternSample night = new LifePatternSample("야간 근무", com.naleul.naleul.domain.lifePattern.enums.LifePatternType.CUSTOM,
                java.time.LocalTime.of(22, 0), java.time.LocalTime.of(7, 0), 1); // 월요일만
        assertThat(night.minutesCovering(DayOfWeek.MONDAY, 23)).isEqualTo(60);
        assertThat(night.minutesCovering(DayOfWeek.TUESDAY, 6)).isEqualTo(60);
        assertThat(night.minutesCovering(DayOfWeek.MONDAY, 6)).isEqualTo(0);
    }

    @Test
    void 골든_타임과_취약_시간() {
        for (LocalDate d : weekdays(10)) {
            f.block(d, 19, true);
            f.block(d, 20, true);
            f.block(d, 21, true);
        }
        List<LocalDate> days = weekdays(10);
        for (int i = 0; i < 10; i++) f.block(days.get(i), 8, i < 3);   // 08시 30%
        for (int i = 0; i < 10; i++) f.block(days.get(i), 12, i < 8);  // 12시 80%

        HeatmapStats h = HeatmapCalculator.calculate(f.set());
        assertThat(h.goldenTime().dayGroup()).isEqualTo(DayGroup.ALL); // 주말 표본 부족 → 평일/주말 구분 안 함
        assertThat(h.goldenTime().startHour()).isEqualTo(19);
        assertThat(h.goldenTime().endHour()).isEqualTo(22);
        assertThat(h.goldenTime().rate()).isEqualTo(100);
        assertThat(h.weakTime().startHour()).isEqualTo(8);
        assertThat(h.weakTime().endHour()).isEqualTo(9);
        assertThat(h.weakTime().rate()).isEqualTo(30);
        assertThat(h.partOfDay().evening()).isEqualTo(100);
        assertThat(h.partOfDay().morning()).isEqualTo(30);
    }

    // ── 요일 · 주차 ───────────────────────────────────────

    @Test
    void 가장_낮은_요일과_가장_많이_놓친_루틴() {
        for (LocalDate d : weekdays(10)) {
            if (d.getDayOfWeek() == DayOfWeek.THURSDAY) {
                f.task().on(d).done(true).add();
                f.task().on(d).routine(44, "근력 운동").done(false).add();
                f.task().on(d).routine(44, "근력 운동").done(false).add();
            } else {
                for (int i = 0; i < 3; i++) f.task().on(d).done(true).add();
            }
        }
        WeekdayStats w = WeekdayCalculator.calculate(f.set());
        assertThat(w.lowest()).isEqualTo(DayOfWeek.THURSDAY);
        assertThat(w.lowestMissed()).isEqualTo("근력 운동");
        assertThat(w.days().get(3).rate()).isEqualTo(33);
        assertThat(w.average()).isEqualTo(87);
    }

    @Test
    void 주차별_흐름은_표본이_적은_주를_비운다() {
        for (int i = 0; i < 5; i++) f.block(START.plusDays(i), 9, true);       // 1주 5개
        for (int i = 0; i < 3; i++) f.block(START.plusDays(7 + i), 9, true);   // 2주 3개
        WeeklyTrendStats t = WeeklyTrendCalculator.calculate(f.set());
        assertThat(t.points()).hasSize(4);
        assertThat(t.points().get(0).rate()).isEqualTo(100);
        assertThat(t.points().get(1).rate()).isNull();
        assertThat(t.maxIndex()).isEqualTo(1);
        assertThat(t.rateStdDev()).isNull();
    }

    // ── 시간 사용 ─────────────────────────────────────────

    @Test
    void 목표별_주_평균_시간과_계획_대비() {
        for (int i = 0; i < 4; i++) {
            LocalDateTime s = START.plusDays(i).atTime(9, 0);
            f.task().goal(1, "토익 900점").at(s).minutes(60).done(true).actual(s, 30).add();
        }
        f.task().goal(2, "이사 준비").temporary(START.plusDays(3)).at(START.atTime(20, 0)).minutes(120).done(true).add();

        TimeUsageStats u = TimeUsageCalculator.calculate(f.set());
        assertThat(u.weeklyTotalHours()).isEqualTo(1.0);
        TimeUsageStats.Goal toeic = u.goals().get(0);
        assertThat(toeic.name()).isEqualTo("토익 900점");
        assertThat(toeic.actualHoursPerWeek()).isEqualTo(0.5);
        assertThat(toeic.plannedHoursPerWeek()).isEqualTo(1.0);
        assertThat(toeic.planRatio()).isEqualTo(50);
        TimeUsageStats.Goal move = u.goals().get(1);
        assertThat(move.planRatio()).isEqualTo(100);
        assertThat(move.newTemporary()).isTrue();
    }

    // ── 블록 지키기 ───────────────────────────────────────

    @Test
    void 제시간_늦게_놓침_비율의_합은_100() {
        for (int i = 0; i < 3; i++) f.block(START.plusDays(i), 9, true);
        LocalDateTime s = START.plusDays(3).atTime(9, 0);
        f.task().at(s).done(true).actual(s.plusMinutes(30), 30).add();
        f.block(START.plusDays(4), 9, false);
        f.block(START.plusDays(5), 9, false);
        f.carries.add(new CarryMissSample(99L, null, START, START.atTime(8, 0)));

        BlockKeepingStats b = BlockKeepingCalculator.calculate(f.set());
        assertThat(b.total()).isEqualTo(7);
        assertThat(b.onTime()).isEqualTo(43);
        assertThat(b.late()).isEqualTo(14);
        assertThat(b.missed()).isEqualTo(43);
    }

    @Test
    void 블록별_제시간_늦게_놓침() {
        for (int i = 0; i < 3; i++) f.task().routine(5, "스트레칭").at(START.plusDays(i).atTime(7, 0)).done(true).add();
        LocalDateTime late = START.plusDays(3).atTime(7, 0);
        f.task().routine(5, "스트레칭").at(late).done(true).actual(late.plusMinutes(40), 20).add();
        f.task().routine(6, "독서").at(START.atTime(22, 0)).done(false).add();
        f.task().routine(6, "독서").at(START.plusDays(1).atTime(22, 0)).done(false).add();
        f.carries.add(new CarryMissSample(77L, 6L, START.plusDays(2), START.plusDays(2).atTime(22, 0)));

        List<BlockKeepingStats.KeepItem> items = BlockKeepingCalculator.calculate(f.set()).items();
        assertThat(items).hasSize(2);
        BlockKeepingStats.KeepItem stretch = items.get(0);
        assertThat(stretch.title()).isEqualTo("스트레칭");
        assertThat(stretch.onTime()).isEqualTo(3);
        assertThat(stretch.late()).isEqualTo(1);
        BlockKeepingStats.KeepItem reading = items.get(1);
        assertThat(reading.missed()).isEqualTo(3);   // 놓친 블록 2 + 이월 놓침 1
        assertThat(reading.routine()).isTrue();
    }

    @Test
    void 자주_옮기는_블록() {
        f.task().routine(7, "토익 RC 30문제").at(START.atTime(8, 0)).add();
        f.task().routine(8, "근력 운동").at(START.plusDays(3).atTime(19, 0)).add();
        for (LocalDate d : weekdays(3)) {
            f.logs.add(TaskScheduleLog.of(1L, 100L, 7L, TaskScheduleAction.USER_MOVE,
                    null, d.atTime(8, 0), null, d.atTime(12, 40), d.atTime(7, 30)));
        }
        for (LocalDate thu : List.of(START.plusDays(3), START.plusDays(10))) {
            f.logs.add(TaskScheduleLog.of(1L, 200L, 8L, TaskScheduleAction.USER_MOVE,
                    null, thu.atTime(19, 0), null, thu.plusDays(1).atTime(19, 0), thu.atTime(18, 0)));
        }
        List<BlockKeepingStats.FrequentMove> moves = BlockKeepingCalculator.calculate(f.set()).frequentMoves();
        assertThat(moves).hasSize(2);
        BlockKeepingStats.FrequentMove rc = moves.get(0);
        assertThat(rc.title()).isEqualTo("토익 RC 30문제");
        assertThat(rc.fromLabel()).isEqualTo("08:00");
        assertThat(rc.toLabel()).isEqualTo("12:40");
        assertThat(rc.description()).isEqualTo("평일 아침 → 점심");
        assertThat(rc.count()).isEqualTo(3);
        BlockKeepingStats.FrequentMove gym = moves.get(1);
        assertThat(gym.dayChange()).isTrue();
        assertThat(gym.fromLabel()).isEqualTo("목");
        assertThat(gym.toLabel()).isEqualTo("금");
        assertThat(gym.description()).isEqualTo("목요일 → 금요일");
    }

    @Test
    void 자주_옮기는_블록은_루틴만_일회성_Task는_제외() {
        f.task().at(START.atTime(9, 0)).add(); // 일회성 Task (taskId 를 알 수 있게 기록만 남김)
        for (LocalDate d : weekdays(3)) {
            f.logs.add(TaskScheduleLog.of(1L, 300L, null, TaskScheduleAction.USER_MOVE,
                    null, d.atTime(9, 0), null, d.atTime(15, 0), d.atTime(8, 0)));
        }
        assertThat(BlockKeepingCalculator.calculate(f.set()).frequentMoves()).isEmpty();
    }

    // ── 루틴 ──────────────────────────────────────────────

    @Test
    void 잘_지키는_루틴과_자주_놓치는_블록() {
        for (int i = 0; i < 10; i++) f.task().routine(1, "아침 체중 기록").on(START.plusDays(i)).done(true).add();
        for (int w = 0; w < 4; w++) {
            f.task().routine(2, "근력 운동").on(START.plusWeeks(w)).done(true).add();               // 월
            f.task().routine(2, "근력 운동").on(START.plusWeeks(w).plusDays(3)).done(w == 0).add(); // 목
        }
        List<LocalDate> days = weekdays(10);
        for (int i = 0; i < 10; i++) f.task().routine(3, "토익 RC").at(days.get(i).atTime(8, 0)).done(i < 5).add();
        f.custom("출근 준비", 7, 0, 8, 0, 31);

        RoutineRankStats r = RoutineRankCalculator.calculate(f.set());
        assertThat(r.kept()).hasSize(1);
        assertThat(r.kept().get(0).title()).isEqualTo("아침 체중 기록");
        assertThat(r.missed()).hasSize(2);
        RoutineRankStats.Missed gym = r.missed().get(0);
        assertThat(gym.label()).isEqualTo("근력 운동 (목)");
        assertThat(gym.description()).isEqualTo("목요일에 자주 놓쳐요");
        assertThat(gym.rate()).isEqualTo(25);
        RoutineRankStats.Missed rc = r.missed().get(1);
        assertThat(rc.label()).isEqualTo("토익 RC (평일 08시)");
        assertThat(rc.description()).isEqualTo("출근 준비와 가까운 시간");
        assertThat(rc.rate()).isEqualTo(50);
    }

    @Test
    void 기록이_적어도_잘_지키는_루틴과_놓치는_루틴이_보인다() {
        for (int i = 0; i < 4; i++) f.task().routine(1, "물 마시기").on(START.plusDays(i)).done(i < 3).add();    // 4번 중 3번 = 75%
        for (int i = 0; i < 3; i++) f.task().routine(2, "영어 단어").on(START.plusDays(i)).done(i == 0).add();   // 3번 중 1번

        RoutineRankStats r = RoutineRankCalculator.calculate(f.set());
        assertThat(r.kept()).extracting(RoutineRankStats.Kept::title).containsExactly("물 마시기");
        assertThat(r.missed()).hasSize(1);
        assertThat(r.missed().get(0).label()).isEqualTo("영어 단어");
        assertThat(r.missed().get(0).description()).isEqualTo("최근 3번 중 2번 놓쳤어요");
    }

    // ── Task 처리 습관 ────────────────────────────────────

    @Test
    void Task_처리_습관() {
        for (int i = 0; i < 5; i++) {
            f.task().on(START.plusDays(3)).done(true).due(START.plusDays(5), START.plusDays(3).atTime(21, 0)).add();
        }
        for (int i = 0; i < 5; i++) {
            f.task().goal(2, "이사 준비").on(START.plusDays(6)).carry(i < 2 ? 1 : 0).add();
        }
        f.brainDumps.add(BrainDumpLog.of(1L, 5, 0, LocalDate.of(2026, 11, 8).atTime(21, 10)));
        f.brainDumps.add(BrainDumpLog.of(1L, 4, 0, LocalDate.of(2026, 11, 15).atTime(22, 0)));
        f.brainDumps.add(BrainDumpLog.of(1L, 6, 1, LocalDate.of(2026, 11, 22).atTime(21, 30)));
        f.brainDumps.add(BrainDumpLog.of(1L, 3, 0, LocalDate.of(2026, 11, 4).atTime(10, 0)));

        TaskHabitStats h = TaskHabitCalculator.calculate(f.set());
        assertThat(h.deadlineLeadDays()).isEqualTo(2.0);
        assertThat(h.deadlineLabel()).isEqualTo("여유 있게 끝내는 편");
        assertThat(h.postponedRate()).isEqualTo(20);
        assertThat(h.postponedTopGoal()).isEqualTo("이사 준비");
        assertThat(h.brainDumpPerWeek()).isEqualTo(1.0);
        assertThat(h.brainDumpAvgItems()).isEqualTo(4.5);
        assertThat(h.brainDumpUsualDay()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(h.brainDumpUsualLabel()).isEqualTo("일요일 밤");
        assertThat(h.brainDumpUsualRange()).isEqualTo("21–23시");
    }

    // ── 전체 ──────────────────────────────────────────────

    @Test
    void 기록이_없어도_예외_없이_빈_결과() {
        PatternStats s = PatternCalculator.calculate(f.set());
        assertThat(s.evaluatedCount()).isEqualTo(0);
        assertThat(s.summary().executionRate()).isNull();
        assertThat(s.heatmap().goldenTime()).isNull();
        assertThat(s.weekday().lowest()).isNull();
        assertThat(s.blockKeeping().onTime()).isNull();
        assertThat(s.routines().kept()).isEmpty();
        assertThat(s.taskHabits().brainDumpPerWeek()).isNull();
    }

    @Test
    void 받침에_따라_과_와() {
        assertThat(TimeLabels.withGwa("출근 준비")).isEqualTo("출근 준비와");
        assertThat(TimeLabels.withGwa("운동")).isEqualTo("운동과");
    }
}
