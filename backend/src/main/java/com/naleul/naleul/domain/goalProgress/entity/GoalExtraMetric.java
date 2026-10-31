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
import java.time.LocalDateTime;

/**
 * 목표형 목표의 "추가 그래프" (V52). 예) SQLD 목표 — 메인: 모의고사 점수 40 → 70점, 추가: 기출 회독 0 → 5회
 *
 * 메인 수치는 예전처럼 goal_category(metric_name · start_value · target_value · current_value) + goal_metric_log 에 그대로 두고,
 * 추가 그래프만 이 테이블에 담는다. 그래서 추가 그래프를 모르는 예전 앱도 메인 그래프는 그대로 보인다.
 * 기간(계획선의 시작 · 끝)은 목표 기간을 같이 쓴다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "goal_metric")
public class GoalExtraMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "goal_metric_id")
    private Long goalMetricId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_category_id", nullable = false)
    private GoalCategory goalCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 30)
    private String metricName;

    @Column(nullable = false, length = 10)
    private String metricUnit;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal startValue;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal targetValue;

    /** 가장 최근 날짜의 기록 (기록이 없으면 시작값) */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal currentValue;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static GoalExtraMetric create(GoalCategory goal, String name, String unit,
                                         BigDecimal startValue, BigDecimal targetValue, int sortOrder) {
        GoalExtraMetric m = new GoalExtraMetric();
        m.goalCategory = goal;
        m.user = goal.getUser();
        m.metricName = name.strip();
        m.metricUnit = unit == null ? "" : unit.strip();
        m.startValue = startValue;
        m.targetValue = targetValue;
        m.currentValue = startValue;
        m.sortOrder = sortOrder;
        return m;
    }

    /** null 이면 그대로 */
    public void update(String name, String unit, BigDecimal startValue, BigDecimal targetValue) {
        if (name != null && !name.isBlank()) this.metricName = name.strip();
        if (unit != null) this.metricUnit = unit.strip();
        if (startValue != null) this.startValue = startValue;
        if (targetValue != null) this.targetValue = targetValue;
    }

    public void updateCurrentValue(BigDecimal value) {
        this.currentValue = value;
    }

    public void delete() {
        this.isDeleted = true;
    }
}
