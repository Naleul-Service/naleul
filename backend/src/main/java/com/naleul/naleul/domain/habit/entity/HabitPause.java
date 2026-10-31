package com.naleul.naleul.domain.habit.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 습관을 쉰 기간 (V52). 쉬는 동안은 잔디맵에서 PAUSED 로, 연속 기록 · 실천률 계산에서 빠진다.
 * endDate 가 null 이면 아직 쉬는 중.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "habit_pause")
public class HabitPause {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long habitPauseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "habit_id", nullable = false)
    private Habit habit;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    public static HabitPause start(Habit habit, LocalDate today) {
        HabitPause p = new HabitPause();
        p.habit = habit;
        p.startDate = today;
        return p;
    }

    /** 다시 시작한 날의 전날까지 쉰 것으로 닫는다 */
    public void close(LocalDate resumeDate) {
        this.endDate = resumeDate.minusDays(1);
    }

    /** 같은 날 쉬었다가 다시 시작하면 쉰 날이 없다 */
    public boolean isEmpty() {
        return endDate != null && endDate.isBefore(startDate);
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
    }
}
