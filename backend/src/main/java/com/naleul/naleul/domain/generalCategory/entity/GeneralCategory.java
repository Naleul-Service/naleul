package com.naleul.naleul.domain.generalCategory.entity;

import com.naleul.naleul.domain.generalCategory.enums.MetricComparator;
import com.naleul.naleul.domain.generalCategory.enums.MetricPeriod;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Builder  // ← 추가
@AllArgsConstructor // ← 추가
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "general_category")
public class GeneralCategory {

    public static final String DEFAULT_BUCKET_NAME = "기타 할 일";
    private static final int DEFAULT_BUCKET_SORT_ORDER = 999;

    // ── PK ────────────────────────────────────────────────
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long generalCategoryId;

    // ── FK (연관관계) ───────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_category_id", nullable = false)
    private GoalCategory goalCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "color_id")
    private UserColor color;

    @OneToMany(mappedBy = "generalCategory", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Routine> routines = new ArrayList<>();

    // ── 일반 컬럼 ───────────────────────────────────────────
    @Column(nullable = false)
    private String generalCategoryName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoalCategoryStatus generalCategoryStatus;

    private LocalDate generalCategoryStartDate;
    private LocalDate generalCategoryEndDate;
    private LocalDate actualEndDate;
    private String achievement;
    private String achievementImageUrl;

    // ── AI 목표 생성용 컬럼 (BE-1) — 세부 목표(SubGoal) 역할 ─────
    private String emoji;

    @Column(length = 300)
    private String description;

    @Column(length = 30)
    private String metricName;

    @Column(length = 10)
    private String metricUnit;

    @Enumerated(EnumType.STRING)
    @Column(length = 5)
    private MetricComparator metricComparator;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private MetricPeriod metricPeriod;

    @Column(precision = 10, scale = 2)
    private BigDecimal targetValue;

    @Column(nullable = false)
    private int sortOrder;

    // ── TimeBlock 명세(3) — "기타 할 일" (V34) ─────────────
    /**
     * 목표마다 하나씩 자동으로 만드는 세부 목표. 세부 목표가 정해지지 않은 Brain dump Task 가 연결된다.
     * TaskActual.generalCategory 가 NOT NULL 이고 통계 쿼리가 세부 목표 기준이라,
     * Task 를 목표에만 연결하지 않고 이 버킷에 넣어 기존 쿼리를 그대로 쓴다.
     * Free 세부 목표 한도(목표당 1개)에서는 제외한다.
     */
    @Column(nullable = false)
    private boolean isDefaultBucket;

    public static GeneralCategory create(String name, User user, GoalCategory goalCategory, UserColor color,
                                         LocalDate generalCategoryStartDate, LocalDate generalCategoryEndDate) {
        GoalCategoryStatus initialStatus = calculateStatusByStartDate(generalCategoryStartDate);

        GeneralCategory generalCategory = new GeneralCategory();
        generalCategory.generalCategoryName = name;
        generalCategory.goalCategory = goalCategory;
        generalCategory.user = user;
        generalCategory.color = color;
        generalCategory.generalCategoryStartDate = generalCategoryStartDate;
        generalCategory.generalCategoryEndDate = generalCategoryEndDate;
        generalCategory.generalCategoryStatus = initialStatus;
        return generalCategory;
    }

    /** 목표의 "기타 할 일" 세부 목표 (목표와 같은 색·기간, 목록 맨 뒤) */
    public static GeneralCategory createDefaultBucket(GoalCategory goal) {
        // 업무형 · 기본 생활 습관(V52)은 마감이 지나도 일이 이어지므로 그릇에 종료일을 두지 않는다
        // (종료일이 있으면 세부 목표 자동 만료 스케줄러가 그릇을 COMPLETED 로 바꿔 버린다)
        LocalDate end = goal.isAchievement() ? goal.getGoalCategoryEndDate() : null;
        GeneralCategory bucket = create(DEFAULT_BUCKET_NAME, goal.getUser(), goal, goal.getUserColor(),
                goal.getGoalCategoryStartDate(), end);
        bucket.isDefaultBucket = true;
        bucket.sortOrder = DEFAULT_BUCKET_SORT_ORDER;
        return bucket;
    }

    private static GoalCategoryStatus calculateStatusByStartDate(LocalDate startDate) {
        if (startDate != null && startDate.isAfter(LocalDate.now())) {
            return GoalCategoryStatus.NOT_STARTED;
        }
        return GoalCategoryStatus.IN_PROGRESS;
    }

    /**
     * AI 초안 확정 시 create() 직후 호출
     */
    public void applyAiPlan(String emoji, String description,
                            String metricName, String metricUnit,
                            MetricComparator metricComparator, MetricPeriod metricPeriod,
                            BigDecimal targetValue, int sortOrder) {
        this.emoji = emoji;
        this.description = description;
        this.metricName = metricName;
        this.metricUnit = metricUnit;
        this.metricComparator = metricComparator;
        this.metricPeriod = metricPeriod;
        this.targetValue = targetValue;
        this.sortOrder = sortOrder;
    }

    public void update(String name, GoalCategory goalCategory, UserColor color,
                       LocalDate generalCategoryStartDate, LocalDate generalCategoryEndDate) {
        if (name != null) {
            this.generalCategoryName = name;
        }
        if (goalCategory != null) {
            this.goalCategory = goalCategory;
        }
        if (color != null) {
            this.color = color;
        }

        LocalDate newStartDate = generalCategoryStartDate != null ? generalCategoryStartDate : this.generalCategoryStartDate;

        if (generalCategoryStartDate != null) {
            this.generalCategoryStartDate = generalCategoryStartDate;
        }
        if (generalCategoryEndDate != null) {
            this.generalCategoryEndDate = generalCategoryEndDate;
        }

        // GoalCategory.update()와 동일: 아직 진행 흐름 중(NOT_STARTED/IN_PROGRESS)일 때만 재계산
        if (this.generalCategoryStatus == GoalCategoryStatus.NOT_STARTED
                || this.generalCategoryStatus == GoalCategoryStatus.IN_PROGRESS) {
            this.generalCategoryStatus = calculateStatusByStartDate(newStartDate);
        }
    }

    public void start() {
        if (this.generalCategoryStatus == GoalCategoryStatus.NOT_STARTED) {
            this.generalCategoryStatus = GoalCategoryStatus.IN_PROGRESS;
        }
    }

    public void complete(LocalDate actualEndDate, String achievement, String achievementImageUrl) {
        if (this.generalCategoryStatus == GoalCategoryStatus.DELETED) {
            throw new CustomException(ErrorCode.GOAL_CATEGORY_ALREADY_DELETED);
        }

        this.generalCategoryStatus = GoalCategoryStatus.COMPLETED;
        this.actualEndDate = actualEndDate;
        this.achievement = achievement;
        this.achievementImageUrl = achievementImageUrl;
    }

    public void expire() {
        if (this.generalCategoryStatus == GoalCategoryStatus.IN_PROGRESS) {
            this.generalCategoryStatus = GoalCategoryStatus.COMPLETED;
        }
    }

    /**
     * 소프트 삭제 처리.
     * 삭제 시 색상 참조를 해제해 UserColor 독립 삭제가 가능하도록 함.
     */
    public void delete() {
        this.generalCategoryStatus = GoalCategoryStatus.DELETED;
        this.color = null;
    }
}
