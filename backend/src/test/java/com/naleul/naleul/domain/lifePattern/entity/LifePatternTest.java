package com.naleul.naleul.domain.lifePattern.entity;

import com.naleul.naleul.domain.lifePattern.enums.LifePatternType;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LifePatternTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 16);

    @Test
    @DisplayName("기본 패턴은 수면 00~07, 점심 12~13, 저녁 18~19 (모두 매일)")
    void defaults() {
        List<LifePattern> defaults = LifePattern.defaults(null);
        assertThat(defaults).extracting(LifePattern::getPatternType)
                .containsExactly(LifePatternType.SLEEP, LifePatternType.LUNCH, LifePatternType.DINNER);
        assertThat(defaults).allMatch(LifePattern::isEveryDay);
        assertThat(defaults.get(0).crossesMidnight()).isFalse(); // 00:00~07:00 은 넘기지 않음
    }

    @Test
    @DisplayName("수면 23:00~07:00 은 자정을 넘겨 다음 날 07시에 끝난다")
    void crossesMidnight() {
        LifePattern sleep = LifePattern.create(null, LifePatternType.SLEEP, "수면", null,
                LocalTime.of(23, 0), LocalTime.of(7, 0), null, 0);
        DateTimeRange r = sleep.occurrenceOn(MON);
        assertThat(sleep.crossesMidnight()).isTrue();
        assertThat(r.start()).isEqualTo(MON.atTime(23, 0));
        assertThat(r.end()).isEqualTo(MON.plusDays(1).atTime(7, 0));
        assertThat(r.minutes()).isEqualTo(8 * 60);
    }

    @Test
    @DisplayName("요일을 지정하면 그 요일에만 블록이 생긴다 (월~금 회사)")
    void weekdaysOnly() {
        LifePattern work = LifePattern.create(null, LifePatternType.CUSTOM, "회사", null,
                LocalTime.of(9, 30), LocalTime.of(18, 0),
                EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), 3);
        assertThat(work.getDaysMask()).isEqualTo(31);
        assertThat(work.occurrenceOn(MON)).isNotNull();
        assertThat(work.occurrenceOn(MON.plusDays(5))).isNull(); // 토요일
        assertThat(work.getDays()).doesNotContain(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    }

    @Test
    @DisplayName("요일을 비우면 매일")
    void emptyDaysMeansEveryDay() {
        assertThat(LifePattern.toMask(List.of())).isEqualTo(LifePattern.EVERY_DAY_MASK);
        assertThat(LifePattern.toMask(null)).isEqualTo(LifePattern.EVERY_DAY_MASK);
    }

    @Test
    @DisplayName("시작과 종료가 같으면 만들 수 없다")
    void sameTimeRejected() {
        assertThatThrownBy(() -> LifePattern.create(null, LifePatternType.CUSTOM, "x", null,
                LocalTime.NOON, LocalTime.NOON, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("설정에서 수정하면 기본값이 바뀐다 (요일은 null이면 유지)")
    void update() {
        LifePattern lunch = LifePattern.defaults(null).get(1);
        lunch.update(null, null, LocalTime.of(12, 30), LocalTime.of(13, 30), null);
        DateTimeRange r = lunch.occurrenceOn(MON);
        assertThat(r.start()).isEqualTo(MON.atTime(12, 30));
        assertThat(lunch.isEveryDay()).isTrue();
    }

    @Test
    @DisplayName("그날만 변경(Override): 월요일 점심을 13~14시로, 비우면 구간 없음")
    void override() {
        LifePattern lunch = LifePattern.defaults(null).get(1);
        LifePatternOverride moved = LifePatternOverride.moved(lunch, 1L, MON, LocalTime.of(13, 0), LocalTime.of(14, 0));
        assertThat(moved.toRange().start()).isEqualTo(MON.atTime(13, 0));
        // 화요일은 패턴 기본값 그대로
        assertThat(lunch.occurrenceOn(MON.plusDays(1)).start()).isEqualTo(MON.plusDays(1).atTime(12, 0));

        moved.skip();
        assertThat(moved.isSkipped()).isTrue();
        assertThat(moved.toRange()).isNull();
    }

    @Test
    @DisplayName("[start, end) 구간: 끝과 시작이 맞닿으면 겹치지 않는다")
    void rangeOverlap() {
        DateTimeRange a = new DateTimeRange(MON.atTime(10, 0), MON.atTime(11, 0));
        DateTimeRange b = new DateTimeRange(MON.atTime(11, 0), MON.atTime(12, 0));
        DateTimeRange c = new DateTimeRange(MON.atTime(10, 30), MON.atTime(11, 30));
        assertThat(a.overlaps(b)).isFalse();
        assertThat(a.overlaps(c)).isTrue();
        assertThat(c.overlaps(b)).isTrue();
    }

    @Test
    @DisplayName("이날만 패턴은 그 날짜에만 블록이 생기고, 같은 요일 다른 주에는 없다")
    void oneOffOnlyOnThatDate() {
        LifePattern p = LifePattern.create(null, LifePatternType.CUSTOM, "병원", null,
                LocalTime.of(14, 0), LocalTime.of(15, 0), null, 0);
        p.restrictTo(MON);
        assertThat(p.isOneOff()).isTrue();
        assertThat(p.occurrenceOn(MON)).isNotNull();
        assertThat(p.occurrenceOn(MON.plusDays(7))).isNull();
        assertThat(p.occurrenceOn(MON.plusDays(1))).isNull();
        assertThat(p.getDays()).containsExactly(DayOfWeek.MONDAY);
    }

    @Test
    @DisplayName("이날만 패턴도 그날 매주 패턴과 겹치면 겹침으로 본다")
    void oneOffOverlap() {
        LifePattern lunch = LifePattern.create(null, LifePatternType.LUNCH, "점심", null,
                LocalTime.of(12, 0), LocalTime.of(13, 0), null, 0);
        LifePattern once = LifePattern.create(null, LifePatternType.CUSTOM, "약속", null,
                LocalTime.of(12, 30), LocalTime.of(14, 0), null, 1);
        once.restrictTo(MON);
        assertThat(com.naleul.naleul.domain.lifePattern.support.FixedBlockCalculator.overlaps(once, lunch)).isTrue();
        LifePattern later = LifePattern.create(null, LifePatternType.CUSTOM, "약속", null,
                LocalTime.of(14, 0), LocalTime.of(15, 0), null, 1);
        later.restrictTo(MON);
        assertThat(com.naleul.naleul.domain.lifePattern.support.FixedBlockCalculator.overlaps(later, lunch)).isFalse();
    }
}
