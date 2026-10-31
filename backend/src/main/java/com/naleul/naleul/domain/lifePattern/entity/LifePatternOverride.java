package com.naleul.naleul.domain.lifePattern.entity;

import com.naleul.naleul.domain.planner.support.DateTimeRange;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 고정 시간의 "그날만" 변경 (명세 3-4).
 * 월요일 점심을 드래그로 옮기면 (점심 패턴, 그 월요일) 행이 생기고, 화요일부터는 패턴 기본값 그대로다.
 * (life_pattern_id, target_date) UNIQUE — 같은 날 다시 옮기면 이 행을 수정한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "life_pattern_override")
public class LifePatternOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lifePatternOverrideId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "life_pattern_id", nullable = false)
    private LifePattern lifePattern;

    /** 날짜 범위 조회를 패턴 조인 없이 하려고 중복 저장 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 블록이 시작하는 날짜 */
    @Column(nullable = false)
    private LocalDate targetDate;

    private LocalTime startTime;
    private LocalTime endTime;

    /** 시작이 target_date 보다 며칠 앞/뒤인지 (-1/0/+1). 예: 토요일 수면(00:00 시작)을 금요일 밤 23:30 으로 → -1 */
    @Column(nullable = false)
    private int startDayOffset;

    @Column(nullable = false)
    private boolean skipped;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public static LifePatternOverride moved(LifePattern pattern, Long userId, LocalDate date,
                                            LocalTime startTime, LocalTime endTime) {
        LifePatternOverride o = new LifePatternOverride();
        o.lifePattern = pattern;
        o.userId = userId;
        o.targetDate = date;
        o.move(startTime, endTime);
        return o;
    }

    public static LifePatternOverride moved(LifePattern pattern, Long userId, LocalDate date,
                                            LocalTime startTime, LocalTime endTime, int startDayOffset) {
        LifePatternOverride o = moved(pattern, userId, date, startTime, endTime);
        o.move(startTime, endTime, startDayOffset);
        return o;
    }

    public static LifePatternOverride skippedOn(LifePattern pattern, Long userId, LocalDate date) {
        LifePatternOverride o = new LifePatternOverride();
        o.lifePattern = pattern;
        o.userId = userId;
        o.targetDate = date;
        o.skip();
        return o;
    }

    public void move(LocalTime startTime, LocalTime endTime) {
        move(startTime, endTime, 0);
    }

    public void move(LocalTime startTime, LocalTime endTime, int startDayOffset) {
        if (startTime == null || endTime == null || startTime.equals(endTime)
                || startDayOffset < -1 || startDayOffset > 1) {
            throw new IllegalArgumentException("invalid override time");
        }
        this.startTime = startTime;
        this.endTime = endTime;
        this.startDayOffset = startDayOffset;
        this.skipped = false;
    }

    public void skip() {
        this.skipped = true;
        this.startTime = null;
        this.endTime = null;
        this.startDayOffset = 0;
    }

    /** 그날 실제 구간. 비운 날이면 null */
    public DateTimeRange toRange() {
        if (skipped) return null;
        return LifePattern.toRange(targetDate, startDayOffset, startTime, endTime);
    }
}
