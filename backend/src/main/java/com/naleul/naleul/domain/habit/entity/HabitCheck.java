package com.naleul.naleul.domain.habit.entity;

import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 습관을 한 날 (V52). 습관 · 날짜당 1행 — 행이 있으면 "함", 체크를 풀면 행을 지운다 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "habit_check",
        uniqueConstraints = @UniqueConstraint(name = "uk_habit_check_day", columnNames = {"habit_id", "check_date"}))
public class HabitCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long habitCheckId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "habit_id", nullable = false)
    private Habit habit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 한국 날짜 */
    @Column(nullable = false)
    private LocalDate checkDate;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static HabitCheck of(Habit habit, LocalDate date) {
        HabitCheck c = new HabitCheck();
        c.habit = habit;
        c.user = habit.getUser();
        c.checkDate = date;
        return c;
    }
}
