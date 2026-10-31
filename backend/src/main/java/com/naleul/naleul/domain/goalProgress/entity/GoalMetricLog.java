package com.naleul.naleul.domain.goalProgress.entity;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 목표 수치 기록 (예: "2주 동안 3kg 감량" 목표의 오늘 몸무게).
 * 목표 · 날짜당 1행 — 같은 날 다시 기록하면 값을 덮어쓴다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "goal_metric_log",
        uniqueConstraints = @UniqueConstraint(name = "uk_gml_goal_date", columnNames = {"goal_category_id", "log_date"}))
public class GoalMetricLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long goalMetricLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_category_id", nullable = false)
    private GoalCategory goalCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDate logDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    @Column(length = 100)
    private String memo;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static GoalMetricLog create(GoalCategory goal, User user, LocalDate date, BigDecimal value, String memo) {
        GoalMetricLog log = new GoalMetricLog();
        log.goalCategory = goal;
        log.user = user;
        log.logDate = date;
        log.value = value;
        log.memo = normalize(memo);
        return log;
    }

    public void change(BigDecimal value, String memo) {
        this.value = value;
        this.memo = normalize(memo);
    }

    private static String normalize(String memo) {
        return memo == null || memo.isBlank() ? null : memo.strip();
    }
}
