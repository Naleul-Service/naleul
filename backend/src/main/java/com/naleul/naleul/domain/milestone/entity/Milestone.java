package com.naleul.naleul.domain.milestone.entity;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.milestone.enums.MilestoneStatus;
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
 * 마일스톤: 전체 목표(GoalCategory)의 시간 구간별 중간 지점.
 * 예) "1개월 차: 76kg" — dueDate + targetValue
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "milestone")
public class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long milestoneId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_category_id", nullable = false)
    private GoalCategory goalCategory;

    @Column(nullable = false, length = 40)
    private String title;

    @Column(length = 300)
    private String description;

    @Column(nullable = false)
    private LocalDate dueDate;

    /** 수치 없는 목표면 null */
    @Column(precision = 10, scale = 2)
    private BigDecimal targetValue;

    /**
     * 사용자가 이 시점 수치를 직접 정했는지 (V40).
     * false 면 MilestoneAutoPlanner 가 목표의 시작값 → 목표값 직선 위에서 날짜 비율대로 계산한다.
     */
    @Column(nullable = false)
    private boolean targetValueManual = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MilestoneStatus status;

    private LocalDateTime achievedAt;

    @Column(nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static Milestone create(GoalCategory goalCategory, String title, String description,
                                   LocalDate dueDate, BigDecimal targetValue, int sortOrder) {
        Milestone milestone = new Milestone();
        milestone.goalCategory = goalCategory;
        milestone.title = title;
        milestone.description = description;
        milestone.dueDate = dueDate;
        milestone.targetValue = targetValue;
        milestone.sortOrder = sortOrder;
        milestone.status = MilestoneStatus.PENDING;
        return milestone;
    }

    public void achieve(LocalDateTime achievedAt) {
        this.status = MilestoneStatus.ACHIEVED;
        this.achievedAt = achievedAt;
    }

    /** null 인 값은 그대로 둔다. description 은 빈 문자열이면 지운다 */
    public void update(String title, String description, LocalDate dueDate, BigDecimal targetValue) {
        if (title != null) this.title = title;
        if (description != null) this.description = description.isBlank() ? null : description;
        if (dueDate != null) this.dueDate = dueDate;
        if (targetValue != null) this.targetValue = targetValue;
    }

    public void clearTargetValue() {
        this.targetValue = null;
        this.targetValueManual = false;
    }

    /** 사용자가 이 시점 수치를 직접 정함 → 자동 계산에서 제외 */
    public void setManualTargetValue(BigDecimal value) {
        this.targetValue = value;
        this.targetValueManual = true;
    }

    /** 자동 계산 값으로 (수동 표시도 해제) */
    public void applyAutoTargetValue(BigDecimal value) {
        this.targetValue = value;
        this.targetValueManual = false;
    }

    /** 자동 보정에서 날짜만 옮길 때 */
    public void moveDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    /** 달성 취소 → 진행 중으로 */
    public void reopen() {
        this.status = MilestoneStatus.PENDING;
        this.achievedAt = null;
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void miss() {
        if (this.status == MilestoneStatus.PENDING) {
            this.status = MilestoneStatus.MISSED;
        }
    }
}
