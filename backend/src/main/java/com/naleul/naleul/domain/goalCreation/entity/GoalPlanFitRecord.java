package com.naleul.naleul.domain.goalCreation.entity;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * AI 목표를 확정할 때의 개인 맞춤 정보 + 초안을 얼마나 고쳤는지 (V47 goal_plan_fit).
 * "개인 맞춤이 효과가 있나"를 재는 용도 — 고친 Task 가 적을수록 잘 맞춘 초안이고,
 * goal_outcome 과 묶으면 강도·상한별 달성률을 비교할 수 있다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "goal_plan_fit",
        uniqueConstraints = @UniqueConstraint(name = "uk_gpf_goal", columnNames = "goal_category_id"))
public class GoalPlanFitRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long goalPlanFitId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_category_id", nullable = false)
    private GoalCategory goalCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String intensity;

    @Column(nullable = false)
    private boolean basedOnHistory;

    private Integer executionRate;

    @Column(nullable = false)
    private int weeklyCapMinutes;

    @Column(nullable = false)
    private int draftWeeklyMinutes;

    @Column(nullable = false)
    private int confirmedWeeklyMinutes;

    @Column(nullable = false)
    private int draftTaskCount;

    @Column(nullable = false)
    private int editedTaskCount;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static GoalPlanFitRecord of(GoalCategory goal, User user, String intensity, boolean basedOnHistory,
                                       Integer executionRate, int weeklyCapMinutes, int draftWeeklyMinutes,
                                       int confirmedWeeklyMinutes, int draftTaskCount, int editedTaskCount) {
        GoalPlanFitRecord r = new GoalPlanFitRecord();
        r.goalCategory = goal;
        r.user = user;
        r.intensity = intensity;
        r.basedOnHistory = basedOnHistory;
        r.executionRate = executionRate;
        r.weeklyCapMinutes = weeklyCapMinutes;
        r.draftWeeklyMinutes = draftWeeklyMinutes;
        r.confirmedWeeklyMinutes = confirmedWeeklyMinutes;
        r.draftTaskCount = draftTaskCount;
        r.editedTaskCount = editedTaskCount;
        return r;
    }
}
