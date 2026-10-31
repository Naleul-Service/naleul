package com.naleul.naleul.domain.pattern.calculator;

import com.naleul.naleul.domain.pattern.entity.TaskScheduleLog;
import com.naleul.naleul.domain.pattern.enums.PatternSuggestionType;
import com.naleul.naleul.domain.pattern.enums.StyleRhythm;
import com.naleul.naleul.domain.pattern.enums.StyleTimeStrength;
import com.naleul.naleul.domain.pattern.enums.TaskScheduleAction;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.naleul.naleul.domain.pattern.calculator.PatternFixtures.START;
import static org.assertj.core.api.Assertions.assertThat;

/** 실행 스타일 · 근거 · 제안 (명세 4 · 3장, 4장) */
class StyleAndSuggestionTest {

    private final PatternFixtures f = new PatternFixtures();
    private static final LocalDate TOMORROW = PatternFixtures.END.plusDays(2);
    private static final Set<DayOfWeek> WEEKDAYS =
            EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    /** 4주 동안 평일 아침 08시 루틴(40% 실행) + 평일 저녁 19~21시 블록(전부 실행) + 주말 블록 */
    private void eveningPerson() {
        for (LocalDate d = START; !d.isAfter(PatternFixtures.END); d = d.plusDays(1)) {
            boolean weekend = d.getDayOfWeek().getValue() >= 6;
            if (weekend) {
                f.block(d, 10, true);
                f.block(d, 11, true);
                f.block(d, 15, d.getDayOfMonth() % 2 == 0);
                continue;
            }
            f.task().routine(31, "토익 RC").at(d.atTime(8, 0)).done(d.getDayOfMonth() % 5 < 2).add();
            f.block(d, 19, true);
            f.block(d, 20, true);
            f.block(d, 21, true);
            f.block(d, 12, d.getDayOfMonth() % 4 != 0);
        }
    }

    private PatternStats stats() {
        return PatternCalculator.calculate(f.set());
    }

    @Test
    void 기록이_적으면_스타일_대신_남은_개수() {
        for (int i = 0; i < 5; i++) f.block(START.plusDays(i), 9, true);
        StyleResult s = StyleClassifier.classify(f.set(), stats());
        assertThat(s.ready()).isFalse();
        assertThat(s.remaining()).isEqualTo(15);
    }

    @Test
    void 저녁에_강한_유형과_근거() {
        eveningPerson();
        StyleResult s = StyleClassifier.classify(f.set(), stats());
        assertThat(s.ready()).isTrue();
        assertThat(s.timeStrength()).isEqualTo(StyleTimeStrength.EVENING);
        assertThat(s.title().startsWith("저녁에 강한 ")).isTrue();
        assertThat(s.insights().get(0).key()).isEqualTo("TIME_STRENGTH");
        assertThat(s.insights().get(0).text().contains("저녁 18–24시 실행률이 **100%**")).isTrue();
    }

    @Test
    void 리듬_판정() {
        assertThat(StyleClassifier.rhythm(null, new WeeklyTrendStats(List.of(), null, 5.0))).isEqualTo(StyleRhythm.STEADY);
        assertThat(StyleClassifier.rhythm(null, new WeeklyTrendStats(List.of(), null, 12.0))).isEqualTo(StyleRhythm.BURST);
    }

    @Test
    void 취약_시간_루틴을_골든_타임으로_옮기는_제안() {
        eveningPerson();
        RoutineInfo rc = new RoutineInfo(31L, "토익 RC", null, LocalTime.of(8, 0), LocalTime.of(8, 30), null,
                WEEKDAYS, true, TOMORROW.plusMonths(3));
        PatternStats st = stats();
        SuggestionResult r = SuggestionFinder.find(f.set(), st, StyleClassifier.classify(f.set(), st),
                List.of(rc), Set.of(), TOMORROW, ConflictChecker.NONE);

        assertThat(r.items()).hasSize(1);
        Suggestion s = r.items().get(0);
        assertThat(s.type()).isEqualTo(PatternSuggestionType.ROUTINE_TIME);
        assertThat(s.afterStart()).isEqualTo(LocalTime.of(19, 0));
        assertThat(s.afterEnd()).isEqualTo(LocalTime.of(19, 30));
        assertThat(s.key()).isEqualTo("ROUTINE_TIME:31:19:00");
        assertThat(s.phrase()).isEqualTo("**토익 RC를 평일 19:00으로**");
        assertThat(r.text().startsWith("아침 블록을 자주 놓치고 저녁에 강한 패턴이에요. **토익 RC를 평일 19:00으로** 고정하면")).isTrue();
        assertThat(r.expectedGain() != null && r.expectedGain() > 0).isTrue();
    }

    @Test
    void 겹치거나_이미_반영한_제안은_빼고_사용자가_자주_옮긴_시각을_우선() {
        eveningPerson();
        RoutineInfo rc = new RoutineInfo(31L, "토익 RC", null, LocalTime.of(8, 0), LocalTime.of(8, 30), null,
                WEEKDAYS, true, TOMORROW.plusMonths(3));
        for (int i = 0; i < 3; i++) {
            LocalDate d = START.plusDays(i);
            f.logs.add(TaskScheduleLog.of(1L, 500L + i, 31L, TaskScheduleAction.USER_MOVE,
                    null, d.atTime(8, 0), null, d.atTime(20, 40), d.atTime(7, 0)));
        }
        PatternStats st = stats();
        SuggestionResult moved = SuggestionFinder.find(f.set(), st, null, List.of(rc), Set.of(), TOMORROW, ConflictChecker.NONE);
        assertThat(moved.items().get(0).afterStart()).isEqualTo(LocalTime.of(20, 40));

        SuggestionResult blocked = SuggestionFinder.find(f.set(), st, null, List.of(rc),
                Set.of("ROUTINE_TIME:31:20:40"), TOMORROW, ConflictChecker.NONE);
        assertThat(blocked).isNull();

        SuggestionResult conflicted = SuggestionFinder.find(f.set(), st, null, List.of(rc), Set.of(), TOMORROW,
                (r, days, s, e) -> 1.0);
        assertThat(conflicted).isNull();
    }

    @Test
    void 특정_요일만_낮은_루틴은_요일을_옮기는_제안() {
        Set<DayOfWeek> monThu = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY);
        for (int w = 0; w < 4; w++) {
            f.task().routine(44, "근력 운동").on(START.plusWeeks(w)).done(true).add();               // 월 100%
            f.task().routine(44, "근력 운동").on(START.plusWeeks(w).plusDays(3)).done(w == 0).add(); // 목 25%
            f.task().on(START.plusWeeks(w).plusDays(4)).done(true).add();                           // 금 다른 일
            f.task().on(START.plusWeeks(w).plusDays(4)).done(true).add();
        }
        for (LocalDate thu : List.of(START.plusDays(3), START.plusDays(10))) {
            f.logs.add(TaskScheduleLog.of(1L, 600L, 44L, TaskScheduleAction.USER_MOVE,
                    null, thu.atTime(19, 0), null, thu.plusDays(1).atTime(19, 0), thu.atTime(18, 0)));
        }
        RoutineInfo gym = new RoutineInfo(44L, "근력 운동", null, null, null, 30, monThu, true, TOMORROW.plusMonths(1));
        PatternStats st = stats();
        SuggestionResult r = SuggestionFinder.find(f.set(), st, null, List.of(gym), Set.of(), TOMORROW, ConflictChecker.NONE);

        Suggestion s = r.items().get(0);
        assertThat(s.type()).isEqualTo(PatternSuggestionType.ROUTINE_DAY);
        assertThat(s.key()).isEqualTo("ROUTINE_DAY:44:THURSDAY:FRIDAY");
        assertThat(s.afterDays()).isEqualTo(List.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
        assertThat(s.phrase()).isEqualTo("**목요일 근력 운동은 금요일로**");
        assertThat(r.text().contains("옮기면 실행률이")).isTrue();
    }

    @Test
    void 끝난_루틴은_제안하지_않는다() {
        eveningPerson();
        RoutineInfo ended = new RoutineInfo(31L, "토익 RC", null, LocalTime.of(8, 0), LocalTime.of(8, 30), null,
                WEEKDAYS, true, TOMORROW.minusDays(1));
        PatternStats st = stats();
        assertThat(SuggestionFinder.find(f.set(), st, null, List.of(ended), Set.of(), TOMORROW, ConflictChecker.NONE)).isNull();
    }

    @Test
    void 조사() {
        assertThat(TimeLabels.withRo("평일 12:40")).isEqualTo("평일 12:40으로");
        assertThat(TimeLabels.withRo("금요일")).isEqualTo("금요일로");
        assertThat(TimeLabels.withRo("07:01")).isEqualTo("07:01로");
        assertThat(TimeLabels.withEulReul("토익 RC 30문제")).isEqualTo("토익 RC 30문제를");
        assertThat(TimeLabels.withEunNeun("근력 운동")).isEqualTo("근력 운동은");
    }
}
