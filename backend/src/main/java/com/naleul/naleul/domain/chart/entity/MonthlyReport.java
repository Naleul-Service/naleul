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
        name = "monthly_report",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "period_start_date"})
)
public class MonthlyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long monthlyReportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 4주 묶음의 첫 주 월요일 ~ 마지막 주 일요일
    @Column(name = "period_start_date", nullable = false)
    private LocalDate periodStartDate;

    @Column(name = "period_end_date", nullable = false)
    private LocalDate periodEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "pattern_type_snapshot", nullable = false)
    private UserPatternType patternTypeSnapshot;

    @Column(name = "ai_feedback_title", columnDefinition = "TEXT")
    private String aiFeedbackTitle;

    @Column(name = "ai_feedback_content", columnDefinition = "TEXT")
    private String aiFeedbackContent;

    @Column(name = "time_pattern_json", columnDefinition = "TEXT")
    private String timePatternJson;

    @Column(name = "weekly_task_count_json", columnDefinition = "TEXT")
    private String weeklyTaskCountJson;

    @Column(name = "routine_weekday_json", columnDefinition = "TEXT")
    private String routineWeekdayJson;

    @Column(name = "routine_ranking_json", columnDefinition = "TEXT")
    private String routineRankingJson;

    @Column(name = "goal_time_distribution_json", columnDefinition = "TEXT")
    private String goalTimeDistributionJson;

    @Column(name = "goal_deviation_json", columnDefinition = "TEXT")
    private String goalDeviationJson;

    @Column(name = "goal_weekly_series_json", columnDefinition = "TEXT")
    private String goalWeeklySeriesJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public MonthlyReport(User user, LocalDate periodStartDate, LocalDate periodEndDate,
                         UserPatternType patternTypeSnapshot,
                         String aiFeedbackTitle, String aiFeedbackContent,
                         String timePatternJson, String weeklyTaskCountJson,
                         String routineWeekdayJson, String routineRankingJson,
                         String goalTimeDistributionJson, String goalDeviationJson,
                         String goalWeeklySeriesJson) {
        this.user = user;
        this.periodStartDate = periodStartDate;
        this.periodEndDate = periodEndDate;
        this.patternTypeSnapshot = patternTypeSnapshot;
        this.aiFeedbackTitle = aiFeedbackTitle;
        this.aiFeedbackContent = aiFeedbackContent;
        this.timePatternJson = timePatternJson;
        this.weeklyTaskCountJson = weeklyTaskCountJson;
        this.routineWeekdayJson = routineWeekdayJson;
        this.routineRankingJson = routineRankingJson;
        this.goalTimeDistributionJson = goalTimeDistributionJson;
        this.goalDeviationJson = goalDeviationJson;
        this.goalWeeklySeriesJson = goalWeeklySeriesJson;
        this.createdAt = LocalDateTime.now();
    }
}