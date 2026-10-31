package com.naleul.naleul.domain.lifePattern.entity;

import com.naleul.naleul.domain.lifePattern.enums.LifePatternType;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 기본 생활 패턴 (고정 시간). 예: 수면 00:00~07:00 매일, 회사 09:30~18:00 월~금.
 *
 * - end_time 이 start_time 보다 이르면(또는 같으면) 자정을 넘기는 블록이다. (수면 23:00~07:00)
 *   단, 시작 = 종료는 의미가 없으므로 create/update 에서 막는다.
 * - 요일은 비트 마스크(days_mask)로 저장한다. 월=1, 화=2, ... 일=64. 127 = 매일.
 *   (루틴처럼 요일 테이블을 따로 두지 않은 이유: 패턴은 사용자당 10개 이하라 조인 없이 한 행으로 충분)
 * - 블록이 "어느 날짜 소속인지"는 시작한 날 기준이다. 요일 판정도 시작한 날의 요일로 한다.
 * - 요일별로 시간을 다르게 할 수 있다 (dayTimes, 예: 주말 수면 01:00~09:30). 없는 요일은 기본 시간.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "life_pattern")
public class LifePattern {

    public static final int EVERY_DAY_MASK = 127;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lifePatternId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LifePatternType patternType;

    @Column(nullable = false, length = 30)
    private String title;

    @Column(length = 16)
    private String emoji;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private int daysMask = EVERY_DAY_MASK;

    @Column(nullable = false)
    private int sortOrder;

    /**
     * 이 날짜에만 있는 고정 시간 (2026-10, 캘린더 "기본 생활 패턴 추가 → 이날만").
     * null 이면 매주 반복. 값이 있으면 그 날짜(의 요일)에만 블록이 생긴다.
     */
    @Column(name = "only_date")
    private LocalDate onlyDate;

    /** 요일별 시간 (기본 시간과 다른 요일만). 사용자당 패턴이 10개 이하라 함께 읽는다 */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "life_pattern_day_time", joinColumns = @JoinColumn(name = "life_pattern_id"))
    @BatchSize(size = 20)
    private List<LifePatternDayTime> dayTimes = new ArrayList<>();

    // DB DEFAULT 값으로 채워지는 컬럼 (애플리케이션에서 쓰지 않음)
    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    // ── 생성 ──────────────────────────────────────────────

    public static LifePattern create(User user, LifePatternType type, String title, String emoji,
                                     LocalTime startTime, LocalTime endTime,
                                     Collection<DayOfWeek> days, int sortOrder) {
        validateTimes(startTime, endTime);
        LifePattern p = new LifePattern();
        p.user = user;
        p.patternType = type;
        p.title = title;
        p.emoji = emoji;
        p.startTime = startTime;
        p.endTime = endTime;
        p.daysMask = toMask(days);
        p.sortOrder = sortOrder;
        return p;
    }

    /**
     * 신규 사용자 기본 패턴 (명세 3-1).
     * "기상 7시 · 수면 12시(자정)"는 수면 블록 하나(00:00~07:00)로 표현한다.
     */
    public static List<LifePattern> defaults(User user) {
        return List.of(
                create(user, LifePatternType.SLEEP, "수면", "🌙", LocalTime.MIDNIGHT, LocalTime.of(7, 0), null, 0),
                create(user, LifePatternType.LUNCH, "점심", "🍚", LocalTime.of(12, 0), LocalTime.of(13, 0), null, 1),
                create(user, LifePatternType.DINNER, "저녁", "🍽️", LocalTime.of(18, 0), LocalTime.of(19, 0), null, 2)
        );
    }

    // ── 수정 (설정 화면에서 바꾸면 이후 모든 날짜의 기본값) ─────────

    public void update(String title, String emoji, LocalTime startTime, LocalTime endTime,
                       Collection<DayOfWeek> days) {
        LocalTime newStart = startTime != null ? startTime : this.startTime;
        LocalTime newEnd = endTime != null ? endTime : this.endTime;
        validateTimes(newStart, newEnd);

        if (title != null) this.title = title;
        if (emoji != null) this.emoji = emoji;
        this.startTime = newStart;
        this.endTime = newEnd;
        if (days != null) this.daysMask = toMask(days);
        // 요일이 빠졌거나 기본 시간과 같아진 요일별 시간은 정리
        dayTimes.removeIf(dt -> !appliesOn(dt.getDayOfWeek()) || dt.sameAs(this.startTime, this.endTime));
    }

    /**
     * 요일별 시간을 통째로 바꾼다 (설정 화면). 빈 목록이면 모든 요일이 기본 시간.
     * 이 패턴이 없는 요일이나 기본 시간과 같은 값은 저장하지 않는다.
     */
    public void replaceDayTimes(Collection<LifePatternDayTime> list) {
        Map<DayOfWeek, LifePatternDayTime> byDay = new LinkedHashMap<>();
        if (list != null) {
            for (LifePatternDayTime dt : list) {
                if (dt == null || !appliesOn(dt.getDayOfWeek()) || dt.sameAs(startTime, endTime)) continue;
                byDay.put(dt.getDayOfWeek(), dt);
            }
        }
        dayTimes.clear();
        dayTimes.addAll(byDay.values());
    }

    /** 한 요일만 앞으로 이 시간으로 (캘린더 "앞으로 ○요일마다") */
    public void setDayTime(DayOfWeek day, LocalTime start, LocalTime end, int startDayOffset) {
        dayTimes.removeIf(dt -> dt.getDayOfWeek() == day);
        LifePatternDayTime dt = new LifePatternDayTime(day, start, end, startDayOffset);
        if (appliesOn(day) && !dt.sameAs(startTime, endTime)) dayTimes.add(dt);
    }

    /** 모든 요일을 기본 시간으로 ("앞으로 매일") */
    public void clearDayTimes() {
        dayTimes.clear();
    }

    public LifePatternDayTime dayTimeOn(DayOfWeek day) {
        for (LifePatternDayTime dt : dayTimes) {
            if (dt.getDayOfWeek() == day) return dt;
        }
        return null;
    }

    // ── 조회 헬퍼 ─────────────────────────────────────────

    /** 종료가 시작보다 이르면 자정을 넘긴다 (예: 23:00~07:00) */
    public boolean crossesMidnight() {
        return crossesMidnight(startTime, endTime);
    }

    /** 이 날짜에 블록이 생기는지 (요일 + 이날만 패턴이면 날짜까지) */
    public boolean appliesOnDate(LocalDate date) {
        if (onlyDate != null && !onlyDate.equals(date)) return false;
        return appliesOn(date.getDayOfWeek());
    }

    public boolean isOneOff() {
        return onlyDate != null;
    }

    /** 이날만 패턴으로 만들기 — 요일도 그 날짜 요일 하나로 맞춘다 */
    public void restrictTo(LocalDate date) {
        this.onlyDate = date;
        this.daysMask = bit(date.getDayOfWeek());
    }

    public boolean appliesOn(DayOfWeek day) {
        return (daysMask & bit(day)) != 0;
    }

    public boolean isEveryDay() {
        return daysMask == EVERY_DAY_MASK;
    }

    public Set<DayOfWeek> getDays() {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) {
            if (appliesOn(d)) days.add(d);
        }
        return days;
    }

    /**
     * date 에 시작하는 이 패턴의 블록 구간 (예외 미반영).
     * 요일이 맞지 않으면 null. 예외(Override)는 FixedBlockResolver 가 덮어쓴다.
     */
    public DateTimeRange occurrenceOn(LocalDate date) {
        if (!appliesOnDate(date)) return null;
        LifePatternDayTime dt = dayTimeOn(date.getDayOfWeek());
        if (dt != null) return toRange(date, dt.getStartDayOffset(), dt.getStartTime(), dt.getEndTime());
        return toRange(date, startTime, endTime);
    }

    // ── static 유틸 ───────────────────────────────────────

    public static boolean crossesMidnight(LocalTime start, LocalTime end) {
        return !end.isAfter(start);
    }

    /** date 에 start 로 시작해 end 로 끝나는 구간. 자정을 넘기면 끝은 다음 날 */
    public static DateTimeRange toRange(LocalDate date, LocalTime start, LocalTime end) {
        return toRange(date, 0, start, end);
    }

    /**
     * date + startDayOffset 일의 start 에 시작해, 그 뒤 처음 오는 end 에 끝나는 구간.
     * 예) (토, -1, 23:30, 09:00) → 금 23:30 ~ 토 09:00
     */
    public static DateTimeRange toRange(LocalDate date, int startDayOffset, LocalTime start, LocalTime end) {
        LocalDateTime s = date.plusDays(startDayOffset).atTime(start);
        LocalDateTime e = s.toLocalDate().atTime(end);
        if (!e.isAfter(s)) e = e.plusDays(1);
        return new DateTimeRange(s, e);
    }

    /** null 이거나 비어 있으면 매일 */
    public static int toMask(Collection<DayOfWeek> days) {
        if (days == null || days.isEmpty()) return EVERY_DAY_MASK;
        int mask = 0;
        for (DayOfWeek d : days) {
            if (d != null) mask |= bit(d);
        }
        return mask == 0 ? EVERY_DAY_MASK : mask;
    }

    private static int bit(DayOfWeek day) {
        return 1 << (day.getValue() - 1); // MONDAY(1) → 1, SUNDAY(7) → 64
    }

    private static void validateTimes(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("startTime/endTime required");
        }
        if (start.equals(end)) {
            // 서비스(BE-2)에서 ErrorCode.LIFE_PATTERN_INVALID_TIME 으로 먼저 걸러야 함
            throw new IllegalArgumentException("startTime and endTime must differ");
        }
    }
}
