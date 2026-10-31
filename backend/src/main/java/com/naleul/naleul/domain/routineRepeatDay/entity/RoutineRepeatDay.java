package com.naleul.naleul.domain.routineRepeatDay.entity;

import com.naleul.naleul.domain.routine.entity.Routine;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "routine_repeat_day",
        // ✅ 같은 루틴에 같은 요일이 중복 저장되는 것을 DB 레벨에서 방지
        uniqueConstraints = @UniqueConstraint(columnNames = {"routine_id", "day_of_week"})
)
public class RoutineRepeatDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id", nullable = false)
    private Routine routine;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DayOfWeek dayOfWeek;

    /**
     * 이 요일만 다른 시간 (V41). 둘 다 null 이면 루틴의 기본 시간(repeatStartTime/EndTime)을 따른다.
     * 예) 월·금 20:00~20:25(기본) + 수요일만 07:00~07:25
     */
    private LocalTime startTime;
    private LocalTime endTime;

    public static RoutineRepeatDay of(Routine routine, DayOfWeek dayOfWeek) {
        RoutineRepeatDay repeatDay = new RoutineRepeatDay();
        repeatDay.routine = routine;
        repeatDay.dayOfWeek = dayOfWeek;
        return repeatDay;
    }

    public boolean hasOwnTime() {
        return startTime != null && endTime != null;
    }

    public void changeTime(LocalTime startTime, LocalTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /** 기본 시간으로 되돌리기 */
    public void clearTime() {
        this.startTime = null;
        this.endTime = null;
    }
}
