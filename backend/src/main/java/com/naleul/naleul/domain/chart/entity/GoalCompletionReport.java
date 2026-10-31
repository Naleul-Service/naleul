package com.naleul.naleul.domain.chart.entity;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.user.entity.User;
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
        name = "goal_completion_report",
        uniqueConstraints = @UniqueConstraint(columnNames = {"goal_category_id"})
)
public class GoalCompletionReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long goalCompletionReportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_category_id", nullable = false)
    private GoalCategory goalCategory;

    // 완료 시점의 스냅샷 (나중에 goalCategory 자체가 바뀌어도 리포트는 그때 값을 유지)
    @Column(name = "goal_category_name_snapshot", nullable = false)
    private String goalCategoryNameSnapshot;

    @Column(name = "achievement_snapshot", columnDefinition = "TEXT")
    private String achievementSnapshot;

    @Column(name = "achievement_image_url_snapshot")
    private String achievementImageUrlSnapshot;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "days_together", nullable = false)
    private long daysTogether;

    @Column(name = "total_minutes", nullable = false)
    private long totalMinutes;

    @Column(name = "execution_count", nullable = false)
    private int executionCount;

    @Column(name = "achievement_rate", nullable = false)
    private double achievementRate;

    @Column(name = "time_pattern_json", columnDefinition = "TEXT")
    private String timePatternJson;

    @Column(name = "weekday_pattern_json", columnDefinition = "TEXT")
    private String weekdayPatternJson;

    @Column(name = "period_pattern_json", columnDefinition = "TEXT")
    private String periodPatternJson;

    @Column(name = "general_category_time_json", columnDefinition = "TEXT")
    private String generalCategoryTimeJson;

    @Column(name = "general_category_ranking_json", columnDefinition = "TEXT")
    private String generalCategoryRankingJson;

    @Column(name = "routine_ranking_json", columnDefinition = "TEXT")
    private String routineRankingJson;

    @Column(name = "general_category_achievement_json", columnDefinition = "TEXT")
    private String generalCategoryAchievementJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public GoalCompletionReport(User user, GoalCategory goalCategory,
                                String goalCategoryNameSnapshot, String achievementSnapshot,
                                String achievementImageUrlSnapshot, LocalDate startDate, LocalDate endDate,
                                long daysTogether, long totalMinutes, int executionCount, double achievementRate,
                                String timePatternJson, String weekdayPatternJson, String periodPatternJson,
                                String generalCategoryTimeJson, String generalCategoryRankingJson,
                                String routineRankingJson, String generalCategoryAchievementJson) {
        this.user = user;
        this.goalCategory = goalCategory;
        this.goalCategoryNameSnapshot = goalCategoryNameSnapshot;
        this.achievementSnapshot = achievementSnapshot;
        this.achievementImageUrlSnapshot = achievementImageUrlSnapshot;
        this.startDate = startDate;
        this.endDate = endDate;
        this.daysTogether = daysTogether;
        this.totalMinutes = totalMinutes;
        this.executionCount = executionCount;
        this.achievementRate = achievementRate;
        this.timePatternJson = timePatternJson;
        this.weekdayPatternJson = weekdayPatternJson;
        this.periodPatternJson = periodPatternJson;
        this.generalCategoryTimeJson = generalCategoryTimeJson;
        this.generalCategoryRankingJson = generalCategoryRankingJson;
        this.routineRankingJson = routineRankingJson;
        this.generalCategoryAchievementJson = generalCategoryAchievementJson;
        this.createdAt = LocalDateTime.now();
    }
}