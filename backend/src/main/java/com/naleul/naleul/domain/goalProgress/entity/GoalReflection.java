package com.naleul.naleul.domain.goalProgress.entity;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalProgress.enums.ReflectionMood;
import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 목표 하루 회고 — 수치(몸무게 등) 옆에 "오늘 무엇을 했는지 · 어땠는지"를 남긴다.
 * 예) 64kg + "점심에 마라탕 먹음, 저녁은 샐러드" + 아쉬워요
 *
 * 왜 따로 두나: 수치 기록(goal_metric_log)은 수치 목표에만 있고 값이 꼭 있어야 한다.
 * 회고는 수치가 없는 목표에도, 수치 없이 글만으로도 남길 수 있어야 해서 별도 테이블로 둔다.
 * 목표 · 날짜당 1행 (같은 날 다시 쓰면 덮어씀). "말로 고치기" AI 가 최근 회고를 근거로 계획을 조정한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "goal_reflection",
        uniqueConstraints = @UniqueConstraint(name = "uk_gr_goal_date", columnNames = {"goal_category_id", "reflect_date"}))
public class GoalReflection {

    public static final int NOTE_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long goalReflectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_category_id", nullable = false)
    private GoalCategory goalCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDate reflectDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ReflectionMood mood;

    @Column(length = NOTE_MAX)
    private String note;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static GoalReflection create(GoalCategory goal, User user, LocalDate date, ReflectionMood mood, String note) {
        GoalReflection r = new GoalReflection();
        r.goalCategory = goal;
        r.user = user;
        r.reflectDate = date;
        r.change(mood, note);
        return r;
    }

    public void change(ReflectionMood mood, String note) {
        this.mood = mood;
        this.note = normalize(note);
    }

    /** 남길 내용이 하나도 없으면 true (이때는 행을 지운다) */
    public static boolean isEmpty(ReflectionMood mood, String note) {
        return mood == null && normalize(note) == null;
    }

    private static String normalize(String note) {
        if (note == null || note.isBlank()) return null;
        String s = note.strip();
        return s.length() > NOTE_MAX ? s.substring(0, NOTE_MAX) : s;
    }
}
