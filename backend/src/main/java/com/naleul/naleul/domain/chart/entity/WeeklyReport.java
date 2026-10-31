package com.naleul.naleul.domain.chart.entity;

import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.enums.UserPatternType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "weekly_report",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "week_start_date"})
)
public class WeeklyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long weeklyReportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_end_date", nullable = false)
    private LocalDate weekEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "pattern_type_snapshot", nullable = false)
    private UserPatternType patternTypeSnapshot;

    @Column(name = "ai_feedback_title", columnDefinition = "TEXT")
    private String aiFeedbackTitle;

    @Column(name = "ai_feedback_content", columnDefinition = "TEXT")
    private String aiFeedbackContent;

    @Column(name = "time_pattern_json", columnDefinition = "TEXT")
    private String timePatternJson;

    @Column(name = "routine_achievement_json", columnDefinition = "TEXT")
    private String routineAchievementJson;

    @Column(name = "goal_time_distribution_json", columnDefinition = "TEXT")
    private String goalTimeDistributionJson;

    @Column(name = "goal_ranking_json", columnDefinition = "TEXT")
    private String goalRankingJson;

    @Column(name = "week_over_week_json", columnDefinition = "TEXT")
    private String weekOverWeekJson;

    // ✅ 신규: 월간 리포트가 재사용할 필드 2개
    @Column(name = "total_task_count", nullable = false)
    private int totalTaskCount;

    @Column(name = "goal_weekly_breakdown_json", columnDefinition = "TEXT")
    private String goalWeeklyBreakdownJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public WeeklyReport(User user, LocalDate weekStartDate, LocalDate weekEndDate,
                        UserPatternType patternTypeSnapshot,
                        String aiFeedbackTitle, String aiFeedbackContent,
                        String timePatternJson, String routineAchievementJson,
                        String goalTimeDistributionJson, String goalRankingJson,
                        String weekOverWeekJson, int totalTaskCount,
                        String goalWeeklyBreakdownJson) {
        this.user = user;
        this.weekStartDate = weekStartDate;
        this.weekEndDate = weekEndDate;
        this.patternTypeSnapshot = patternTypeSnapshot;
        this.aiFeedbackTitle = aiFeedbackTitle;
        this.aiFeedbackContent = aiFeedbackContent;
        this.timePatternJson = timePatternJson;
        this.routineAchievementJson = routineAchievementJson;
        this.goalTimeDistributionJson = goalTimeDistributionJson;
        this.goalRankingJson = goalRankingJson;
        this.weekOverWeekJson = weekOverWeekJson;
        this.totalTaskCount = totalTaskCount;
        this.goalWeeklyBreakdownJson = goalWeeklyBreakdownJson;
        this.createdAt = LocalDateTime.now();
    }
}