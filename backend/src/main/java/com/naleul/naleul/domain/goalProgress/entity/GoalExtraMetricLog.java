package com.naleul.naleul.domain.goalProgress.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 추가 그래프의 날짜별 기록 (V52) — 그래프 · 날짜당 1행, 같은 날 다시 기록하면 덮어쓴다 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "goal_metric_value",
        uniqueConstraints = @UniqueConstraint(name = "uk_gmv_metric_date", columnNames = {"goal_metric_id", "log_date"}))
public class GoalExtraMetricLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "goal_metric_value_id")
    private Long goalMetricValueId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_metric_id", nullable = false)
    private GoalExtraMetric metric;

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

    public static GoalExtraMetricLog create(GoalExtraMetric metric, LocalDate date, BigDecimal value, String memo) {
        GoalExtraMetricLog l = new GoalExtraMetricLog();
        l.metric = metric;
        l.logDate = date;
        l.value = value;
        l.memo = normalize(memo);
        return l;
    }

    public void change(BigDecimal value, String memo) {
        this.value = value;
        if (memo != null) this.memo = normalize(memo);
    }

    private static String normalize(String memo) {
        return memo == null || memo.isBlank() ? null : memo.strip();
    }
}
