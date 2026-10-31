package com.naleul.naleul.domain.lifePattern.support;

import com.naleul.naleul.domain.lifePattern.entity.LifePattern;
import com.naleul.naleul.domain.lifePattern.entity.LifePatternOverride;
import com.naleul.naleul.domain.lifePattern.enums.LifePatternType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FixedBlockCalculatorTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 16);
    private long seq = 1;

    private LifePattern pattern(LifePatternType type, String title, int sh, int sm, int eh, int em,
                                EnumSet<DayOfWeek> days) {
        LifePattern p = LifePattern.create(null, type, title, null,
                LocalTime.of(sh, sm), LocalTime.of(eh, em), days, 0);
        ReflectionTestUtils.setField(p, "lifePatternId", seq++);
        return p;
    }

    @Test
    @DisplayName("기본 패턴: 블록 3개, 하루 범위 07:00 ~ 다음 날 00:00")
    void defaults() {
        List<LifePattern> ps = LifePattern.defaults(null);
        ps.forEach(p -> ReflectionTestUtils.setField(p, "lifePatternId", seq++));

        DayFixedBlocks day = FixedBlockCalculator.calculate(ps, List.of(), MON, MON).get(MON);

        assertThat(day.displayBlocks()).extracting(FixedBlock::title).containsExactly("수면", "점심", "저녁");
        assertThat(day.dayRange().start()).isEqualTo(MON.atTime(7, 0));
        assertThat(day.dayRange().end()).isEqualTo(MON.plusDays(1).atStartOfDay());
    }

    @Test
    @DisplayName("수면 23:00~07:00: 월요일 화면엔 23~24시, 화요일 화면엔 00~07시(잘림 표시), 하루 범위 07~23시")
    void sleepCrossingMidnight() {
        LifePattern sleep = pattern(LifePatternType.SLEEP, "수면", 23, 0, 7, 0, null);
        Map<LocalDate, DayFixedBlocks> r = FixedBlockCalculator.calculate(List.of(sleep), List.of(), MON, MON.plusDays(1));

        FixedBlock monBlock = r.get(MON).displayBlocks().get(r.get(MON).displayBlocks().size() - 1);
        assertThat(monBlock.start()).isEqualTo(MON.atTime(23, 0));
        assertThat(monBlock.end()).isEqualTo(MON.plusDays(1).atStartOfDay());

        FixedBlock tueFirst = r.get(MON.plusDays(1)).displayBlocks().get(0);
        assertThat(tueFirst.clippedFromPreviousDay()).isTrue();
        assertThat(tueFirst.targetDate()).isEqualTo(MON);           // 드래그하면 월요일 Override
        assertThat(tueFirst.end()).isEqualTo(MON.plusDays(1).atTime(7, 0));

        assertThat(r.get(MON.plusDays(1)).dayRange().start()).isEqualTo(MON.plusDays(1).atTime(7, 0));
        assertThat(r.get(MON.plusDays(1)).dayRange().end()).isEqualTo(MON.plusDays(1).atTime(23, 0));
    }

    @Test
    @DisplayName("월요일 점심만 13~14시로 변경: 월요일만 바뀌고 화요일은 12~13시")
    void overrideOnlyThatDay() {
        LifePattern lunch = pattern(LifePatternType.LUNCH, "점심", 12, 0, 13, 0, null);
        LifePatternOverride o = LifePatternOverride.moved(lunch, 1L, MON, LocalTime.of(13, 0), LocalTime.of(14, 0));

        Map<LocalDate, DayFixedBlocks> r = FixedBlockCalculator.calculate(List.of(lunch), List.of(o), MON, MON.plusDays(1));

        FixedBlock mon = r.get(MON).displayBlocks().get(0);
        assertThat(mon.start()).isEqualTo(MON.atTime(13, 0));
        assertThat(mon.overridden()).isTrue();
        assertThat(r.get(MON.plusDays(1)).displayBlocks().get(0).start()).isEqualTo(MON.plusDays(1).atTime(12, 0));
    }

    @Test
    @DisplayName("그날만 비우기: 그날 블록 없음")
    void skipped() {
        LifePattern dinner = pattern(LifePatternType.DINNER, "저녁", 18, 0, 19, 0, null);
        LifePatternOverride o = LifePatternOverride.skippedOn(dinner, 1L, MON);
        DayFixedBlocks day = FixedBlockCalculator.calculate(List.of(dinner), List.of(o), MON, MON).get(MON);
        assertThat(day.displayBlocks()).isEmpty();
    }

    @Test
    @DisplayName("요일 지정: 회사(월~금)는 토요일에 없다")
    void weekdaysOnly() {
        LifePattern work = pattern(LifePatternType.CUSTOM, "회사", 9, 30, 18, 0,
                EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
        LocalDate sat = MON.plusDays(5);
        Map<LocalDate, DayFixedBlocks> r = FixedBlockCalculator.calculate(List.of(work), List.of(), MON, MON.plusDays(6));
        assertThat(r.get(MON).displayBlocks()).hasSize(1);
        assertThat(r.get(sat).displayBlocks()).isEmpty();
    }

    @Test
    @DisplayName("수면 패턴이 없으면 하루 범위 00:00~24:00")
    void noSleep() {
        DayFixedBlocks day = FixedBlockCalculator.calculate(List.of(), List.of(), MON, MON).get(MON);
        assertThat(day.dayRange().start()).isEqualTo(MON.atStartOfDay());
        assertThat(day.dayRange().end()).isEqualTo(MON.plusDays(1).atStartOfDay());
    }

    @Test
    @DisplayName("겹침 검사: 같은 요일 시간이 겹치면 true, 요일이 다르면 false, 자정 넘김도 검사")
    void overlaps() {
        LifePattern lunch = pattern(LifePatternType.LUNCH, "점심", 12, 0, 13, 0, null);
        LifePattern meeting = pattern(LifePatternType.CUSTOM, "회의", 12, 30, 13, 30, null);
        LifePattern weekendBrunch = pattern(LifePatternType.CUSTOM, "브런치", 12, 30, 13, 30,
                EnumSet.of(DayOfWeek.SATURDAY));
        LifePattern lunchWeekday = pattern(LifePatternType.LUNCH, "점심", 12, 0, 13, 0,
                EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
        LifePattern sleep = pattern(LifePatternType.SLEEP, "수면", 23, 0, 7, 0, null);
        LifePattern earlyRun = pattern(LifePatternType.CUSTOM, "새벽 러닝", 6, 0, 6, 30, null);
        LifePattern breakfast = pattern(LifePatternType.CUSTOM, "아침", 7, 0, 7, 30, null);

        assertThat(FixedBlockCalculator.overlaps(lunch, meeting)).isTrue();
        assertThat(FixedBlockCalculator.overlaps(lunchWeekday, weekendBrunch)).isFalse();
        assertThat(FixedBlockCalculator.overlaps(sleep, earlyRun)).isTrue();   // 다음 날 새벽과 겹침
        assertThat(FixedBlockCalculator.overlaps(sleep, breakfast)).isFalse(); // 07:00 에 맞닿기만 함
    }

    @Test
    @DisplayName("요일별 시간: 수면 00:00~07:00, 토요일만 01:00~09:30 → 토요일 기상 09:30, 금요일 밤 취침 01:00")
    void dayTimes_weekendSleep() {
        LifePattern sleep = pattern(LifePatternType.SLEEP, "수면", 0, 0, 7, 0, null);
        sleep.setDayTime(DayOfWeek.SATURDAY, LocalTime.of(1, 0), LocalTime.of(9, 30), 0);
        LocalDate fri = MON.plusDays(4);
        LocalDate sat = MON.plusDays(5);
        Map<LocalDate, DayFixedBlocks> r = FixedBlockCalculator.calculate(List.of(sleep), List.of(), fri, sat);

        assertThat(r.get(sat).dayRange().start()).isEqualTo(sat.atTime(9, 30));
        assertThat(r.get(fri).dayRange().end()).isEqualTo(sat.atTime(1, 0));   // 금요일 하루는 토요일 01시까지
        assertThat(r.get(fri).dayRange().start()).isEqualTo(fri.atTime(7, 0)); // 평일은 그대로
        assertThat(r.get(fri).bedSleep().targetDate()).isEqualTo(sat);
        assertThat(r.get(sat).wakeSleep().targetDate()).isEqualTo(sat);
    }

    @Test
    @DisplayName("시작 날짜 차이: 토요일 수면(00:00)을 금요일 23:30 취침으로 바꾸면 금요일 화면에 23:30~24:00 이 보이고 금요일 하루가 23:30 에 끝난다")
    void override_startDayOffset() {
        LifePattern sleep = pattern(LifePatternType.SLEEP, "수면", 0, 0, 7, 0, null);
        LocalDate fri = MON.plusDays(4);
        LocalDate sat = MON.plusDays(5);
        LifePatternOverride o = LifePatternOverride.moved(sleep, 1L, sat, LocalTime.of(23, 30), LocalTime.of(7, 0), -1);
        Map<LocalDate, DayFixedBlocks> r = FixedBlockCalculator.calculate(List.of(sleep), List.of(o), fri, sat);

        assertThat(r.get(fri).dayRange().end()).isEqualTo(fri.atTime(23, 30));
        FixedBlock last = r.get(fri).displayBlocks().get(r.get(fri).displayBlocks().size() - 1);
        assertThat(last.start()).isEqualTo(fri.atTime(23, 30));
        assertThat(last.targetDate()).isEqualTo(sat);   // 소속은 그대로 토요일
        assertThat(r.get(sat).dayRange().start()).isEqualTo(sat.atTime(7, 0));
    }

    @Test
    @DisplayName("요일별 시간끼리 겹치면 overlapsItself = true")
    void overlapsItself() {
        LifePattern sleep = pattern(LifePatternType.SLEEP, "수면", 0, 0, 7, 0, null);
        assertThat(FixedBlockCalculator.overlapsItself(sleep)).isFalse();
        sleep.setDayTime(DayOfWeek.SATURDAY, LocalTime.of(6, 0), LocalTime.of(9, 0), -1); // 금 06:00 시작 → 금 00~07시와 겹침
        assertThat(FixedBlockCalculator.overlapsItself(sleep)).isTrue();
    }

    @Test
    @DisplayName("늦잠: 그날만 오후 2시에 일어나도(00:00~14:00) 기상 블록으로 보고, 하루는 14시부터")
    void lateWake_afternoon() {
        LifePattern sleep = pattern(LifePatternType.SLEEP, "수면", 0, 0, 8, 0, null);
        LocalDate wed = MON.plusDays(2);
        LifePatternOverride o = LifePatternOverride.moved(sleep, 1L, wed, LocalTime.of(0, 0), LocalTime.of(14, 0));
        Map<LocalDate, DayFixedBlocks> r = FixedBlockCalculator.calculate(List.of(sleep), List.of(o), wed, wed);

        DayFixedBlocks day = r.get(wed);
        assertThat(day.wakeSleep()).isNotNull();
        assertThat(day.wakeSleep().end()).isEqualTo(wed.atTime(14, 0));
        assertThat(day.dayRange().start()).isEqualTo(wed.atTime(14, 0));
        // 다음 날 수면(목 00:00)이 이날의 취침
        assertThat(day.bedSleep()).isNotNull();
        assertThat(day.dayRange().end()).isEqualTo(wed.plusDays(1).atStartOfDay());
    }

    @Test
    @DisplayName("늦잠: 전날 23:30 에 자서 다음 날 오후 2시에 일어나도 기상 블록")
    void lateWake_fromPreviousNight() {
        LifePattern sleep = pattern(LifePatternType.SLEEP, "수면", 23, 30, 8, 0, null);
        LocalDate tue = MON.plusDays(1);
        LocalDate wed = MON.plusDays(2);
        LifePatternOverride o = LifePatternOverride.moved(sleep, 1L, tue, LocalTime.of(23, 30), LocalTime.of(14, 0));
        Map<LocalDate, DayFixedBlocks> r = FixedBlockCalculator.calculate(List.of(sleep), List.of(o), tue, wed);

        assertThat(r.get(wed).wakeSleep()).isNotNull();
        assertThat(r.get(wed).dayRange().start()).isEqualTo(wed.atTime(14, 0));
        assertThat(r.get(tue).bedSleep().start()).isEqualTo(tue.atTime(23, 30));
    }
}
