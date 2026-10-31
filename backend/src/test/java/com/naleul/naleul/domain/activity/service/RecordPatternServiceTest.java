package com.naleul.naleul.domain.activity.service;

import com.naleul.naleul.domain.activity.dto.response.RecordPatternResponse;
import com.naleul.naleul.domain.activity.service.RecordPatternService.Entry;
import com.naleul.naleul.domain.activity.service.RecordPatternService.RoutineDone;
import com.naleul.naleul.domain.activity.service.RecordPatternService.RoutineInfo;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 기록형 목표 "나의 패턴" 계산 — 완료한 Task 기준 (DB 없이 순수 로직) */
class RecordPatternServiceTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 9);   // 금요일

    /** 시각·시간이 있는 완료 Task */
    private static Entry e(String title, LocalDate date, int hour, int minutes) {
        return new Entry(date, date.atTime(hour, 0), (long) minutes);
    }

    private static RecordPatternResponse compute(List<Entry> entries) {
        return RecordPatternService.compute(entries, List.of(), List.of(), TODAY);
    }

    @Test
    void 기록이_없으면_빈_통계() {
        RecordPatternResponse r = compute(List.of());

        assertThat(r.summary().totalCount()).isZero();
        assertThat(r.summary().firstDoneDate()).isNull();
        assertThat(r.summary().weekStreak()).isZero();
        assertThat(r.rhythm().weeks()).hasSize(12);
        assertThat(r.when().peakDay()).isNull();
        assertThat(r.routines().items()).isEmpty();
    }

    @Test
    void 이번_주가_비어도_지난주까지_이어진_연속_주는_유지된다() {
        LocalDate lastWeekMon = LocalDate.of(2026, 9, 28);
        RecordPatternResponse r = compute(List.of(
                e("회의", lastWeekMon, 10, 60),
                e("회의", lastWeekMon.minusWeeks(1), 10, 60),
                e("회의", lastWeekMon.minusWeeks(2), 10, 60)));

        assertThat(r.summary().weekStreak()).isEqualTo(3);
        assertThat(r.summary().thisWeekDone()).isFalse();
    }

    @Test
    void 주차별_누적은_줄지_않고_마지막_주에_총합과_같다() {   // 개수·시간 모두
        RecordPatternResponse r = compute(List.of(
                e("기획", TODAY.minusWeeks(20), 20, 120),   // 12주 그래프보다 이전 → 누적 시작값에 포함
                e("기획", TODAY.minusWeeks(3), 20, 30),
                e("기획", TODAY, 20, 45)));

        List<RecordPatternResponse.WeekStat> weeks = r.rhythm().weeks();
        assertThat(weeks.get(0).cumulativeCount()).isEqualTo(1);
        assertThat(weeks.get(0).cumulativeMinutes()).isEqualTo(120);
        assertThat(weeks.get(weeks.size() - 1).cumulativeCount()).isEqualTo(3);
        assertThat(weeks.get(weeks.size() - 1).cumulativeMinutes()).isEqualTo(195);
        for (int i = 1; i < weeks.size(); i++) {
            assertThat(weeks.get(i).cumulativeCount()).isGreaterThanOrEqualTo(weeks.get(i - 1).cumulativeCount());
        }
    }

    @Test
    void 요일과_시간대별로_가장_많이_한_때를_찾는다() {
        List<Entry> list = new ArrayList<>();
        for (int w = 0; w < 4; w++) {
            LocalDate mon = LocalDate.of(2026, 10, 5).minusWeeks(w);
            list.add(e("공부", mon.plusDays(1), 20, 90));   // 화 저녁
            list.add(e("공부", mon.plusDays(3), 9, 30));    // 목 오전
        }
        RecordPatternResponse r = compute(list);

        // 화 저녁 4번 · 목 오전 4번 — 개수가 같으면 먼저 오는 요일
        assertThat(r.when().peakDay()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(r.when().peakBand()).isEqualTo("EVENING");
        assertThat(r.summary().recent().count()).isEqualTo(8);
        assertThat(r.summary().recent().minutes()).isEqualTo(480);
    }

    @Test
    void 시각을_모르는_Task는_개수에만_들어가고_언제_표에서는_빠진다() {
        List<Entry> list = List.of(
                e("회의", TODAY.minusDays(1), 20, 60),
                e("회의", TODAY.minusDays(8), 20, 60),
                new Entry(TODAY.minusDays(2), null, null));   // 시간 미정으로 완료
        RecordPatternResponse r = compute(list);

        assertThat(r.summary().totalCount()).isEqualTo(3);
        assertThat(r.summary().totalMinutes()).isEqualTo(120);
        assertThat(r.when().untimedCount()).isEqualTo(1);
        assertThat(r.when().peakDay()).isEqualTo(TODAY.minusDays(1).getDayOfWeek());
        assertThat(r.when().peakBand()).isEqualTo("EVENING");
    }

    @Test
    void Task_날짜는_실제_계획_루틴날짜_권장일_완료시각_순으로_정한다() {
        LocalDateTime planned = TODAY.minusDays(3).atTime(9, 0);
        Entry byPlan = Entry.of(null, null, planned, planned.plusMinutes(50), null, null, null, TODAY.atTime(23, 0));
        assertThat(byPlan.date()).isEqualTo(TODAY.minusDays(3));
        assertThat(byPlan.minutes()).isEqualTo(50);
        assertThat(byPlan.band()).isEqualTo("MORNING");

        Entry byRoutineDate = Entry.of(null, null, null, null, null, TODAY.minusDays(5), null, TODAY.atTime(23, 0));
        assertThat(byRoutineDate.date()).isEqualTo(TODAY.minusDays(5));
        assertThat(byRoutineDate.band()).isNull();   // 완료 버튼 누른 시각은 "언제 했는지"로 쓰지 않는다

        Entry byCompletedAt = Entry.of(null, null, null, null, null, null, null, TODAY.atTime(23, 0));
        assertThat(byCompletedAt.date()).isEqualTo(TODAY);

        assertThat(Entry.of(null, null, null, null, null, null, null, null)).isNull();
    }

    @Test
    void 루틴별_시간은_실제_시각이_없으면_루틴_기본_시간으로_센다() {
        List<RoutineInfo> routines = List.of(
                new RoutineInfo(1L, "아침 스트레칭", null, 20),
                new RoutineInfo(2L, "주간 회고", null, null));
        List<RoutineDone> dones = List.of(
                new RoutineDone(1L, TODAY.minusDays(1), null),   // 기본 20분
                new RoutineDone(1L, TODAY.minusDays(2), 35L),    // 실제 35분
                new RoutineDone(2L, TODAY.minusDays(3), 60L));
        RecordPatternResponse r = RecordPatternService.compute(List.of(), routines, dones, TODAY);

        assertThat(r.routines().items()).extracting(RecordPatternResponse.RoutineStat::routineName)
                .containsExactly("주간 회고", "아침 스트레칭");
        RecordPatternResponse.RoutineStat stretch = r.routines().items().get(1);
        assertThat(stretch.minutes()).isEqualTo(55);
        assertThat(stretch.completedCount()).isEqualTo(2);
    }

    @Test
    void 최근_4주에_더_자주_한_루틴을_요즘_늘어난_루틴으로_꼽는다() {
        List<RoutineInfo> routines = List.of(
                new RoutineInfo(1L, "독서", null, 30),
                new RoutineInfo(2L, "정리", null, 30));
        List<RoutineDone> dones = new ArrayList<>();
        dones.add(new RoutineDone(1L, TODAY.minusDays(40), null));             // 그 전 4주 1번
        for (int i = 0; i < 4; i++) dones.add(new RoutineDone(1L, TODAY.minusDays(i * 3), null));   // 최근 4번
        dones.add(new RoutineDone(2L, TODAY.minusDays(35), null));
        dones.add(new RoutineDone(2L, TODAY.minusDays(2), null));              // 그대로
        RecordPatternResponse r = RecordPatternService.compute(List.of(), routines, dones, TODAY);

        assertThat(r.routines().risingRoutineId()).isEqualTo(1L);
    }

    @Test
    void 루틴이_없으면_빈_목록() {
        RecordPatternResponse r = RecordPatternService.compute(List.of(e("회의", TODAY, 10, 60)), List.of(), List.of(), TODAY);
        assertThat(r.routines().items()).isEmpty();
        assertThat(r.routines().risingRoutineId()).isNull();
    }
}
