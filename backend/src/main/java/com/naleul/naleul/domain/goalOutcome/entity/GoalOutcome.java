package com.naleul.naleul.domain.goalOutcome.entity;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.kind.GoalSubType;
import com.naleul.naleul.domain.goalCategory.kind.GoalType;
import com.naleul.naleul.domain.goalOutcome.enums.AutoReason;
import com.naleul.naleul.domain.goalOutcome.enums.OutcomePromptStatus;
import com.naleul.naleul.domain.goalOutcome.enums.OutcomeResult;
import com.naleul.naleul.domain.goalOutcome.service.GoalOutcomeCalculator;
import com.naleul.naleul.domain.goalOutcome.enums.OutcomeSource;
import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 목표가 끝났을 때의 결과 (V47). 목표당 1행 — 다시 정하면 덮어쓴다.
 *
 * 왜 따로 두나: goal_category.status 의 COMPLETED 는 "끝남"일 뿐이라, 끝까지 해낸 목표와 기간만 지난 목표를
 * 구분할 수 없다. 결과와 그때의 실천률을 남겨야 "이렇게 계획한 사람이 달성했다"를 알 수 있다.
 * 같은 카테고리 달성자 익명 집계(AchieverStats)의 재료.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "goal_outcome",
        uniqueConstraints = @UniqueConstraint(name = "uk_go_goal", columnNames = "goal_category_id"))
public class GoalOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long goalOutcomeId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_category_id", nullable = false)
    private GoalCategory goalCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private GoalType goalType;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private GoalSubType goalSubType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutcomeResult result;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OutcomeSource source;

    private Integer plannedDays;
    private Integer actualDays;
    private Integer metricPercent;
    private Integer routineRate;
    private Integer taskRate;

    @Column(nullable = false)
    private int routineCount;

    @Column(nullable = false)
    private int keptRoutineCount;

    @Column(nullable = false)
    private int weeklyRoutineMinutes;

    @Column(nullable = false)
    private boolean aiGenerated;

    /** 수치 목표인데 수치를 기록하지 않아 실천률로 판정했다 (V49) */
    @Column(nullable = false)
    private boolean metricUnrecorded;

    /**
     * 서버가 수치·실천률로 낸 판정 (V49). 사용자가 결과를 직접 골라도 이 값은 그대로 남는다.
     * → result 와 비교해서 "자동 판정이 사용자 생각과 얼마나 맞나"(판정 일치율)를 본다.
     * V49 이전에 사용자가 고른 결과는 자동 판정을 남기지 않아서 null.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OutcomeResult autoResult;

    /** 기간 중 아무 활동이 없던 가장 긴 구간 (일, V49). 14일 이상이면 중간에 멈췄던 목표 */
    private Integer longestIdleDays;

    /** 자동 판정에 무엇을 썼는지 (V63) — 안내 문구 · 대시보드 분류용 */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AutoReason autoReason;

    /** 결과 확인 모달 상태 (V63). 자동 판정된 목표만 PENDING 으로 시작, 사용자가 직접 마무리한 목표는 null */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OutcomePromptStatus promptStatus;

    /** 모달에서 답했거나 "나중에"를 누른 시각 (V63) */
    private LocalDateTime promptRespondedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static GoalOutcome create(GoalCategory goal, User user) {
        GoalOutcome o = new GoalOutcome();
        o.goalCategory = goal;
        o.user = user;
        return o;
    }

    /**
     * 결과와 스냅샷을 (다시) 채운다
     * @param autoResult 스냅샷으로 낸 자동 판정 (사용자가 고른 결과와 같을 수도, 다를 수도 있다)
     */
    public void fill(OutcomeResult result, OutcomeResult autoResult, OutcomeSource source, GoalType type,
                     GoalSubType subType, OutcomeSnapshot s, boolean aiGenerated) {
        this.result = result;
        this.autoResult = autoResult;
        this.source = source;
        this.goalType = type;
        this.goalSubType = subType;
        this.plannedDays = s.plannedDays();
        this.actualDays = s.actualDays();
        this.metricPercent = s.metricPercent();
        this.routineRate = s.routineRate();
        this.taskRate = s.taskRate();
        this.routineCount = s.routineCount();
        this.keptRoutineCount = s.keptRoutineCount();
        this.weeklyRoutineMinutes = s.weeklyRoutineMinutes();
        this.aiGenerated = aiGenerated;
        this.metricUnrecorded = s.metricUnrecorded();
        this.longestIdleDays = s.longestIdleDays();
        this.autoReason = GoalOutcomeCalculator.reasonOf(s);
        // 기간이 끝나 자동 판정된 목표만 "목표는 만족했나요?" 를 묻는다 (한 번 답했거나 미룬 건 다시 묻지 않는다)
        if (source == OutcomeSource.AUTO && this.promptStatus == null) this.promptStatus = OutcomePromptStatus.PENDING;
        // 묻기 전에 다른 경로(목표 마무리 등)로 직접 골랐으면 다시 묻지 않는다
        if (source == OutcomeSource.USER && this.promptStatus == OutcomePromptStatus.PENDING) {
            this.promptStatus = OutcomePromptStatus.ANSWERED;
            this.promptRespondedAt = LocalDateTime.now();
        }
    }

    /** 끝난 뒤 사용자가 결과만 다시 고를 때 (스냅샷 · 자동 판정은 그대로) */
    public void changeResult(OutcomeResult result, LocalDateTime now) {
        this.result = result;
        this.source = OutcomeSource.USER;
        if (this.promptStatus == OutcomePromptStatus.PENDING || this.promptStatus == OutcomePromptStatus.DISMISSED) {
            this.promptStatus = OutcomePromptStatus.ANSWERED;
            this.promptRespondedAt = now;
        }
    }

    /** 결과 확인 모달 "나중에" — 자동 판정 결과를 그대로 두고 다시 묻지 않는다 */
    public void dismissPrompt(LocalDateTime now) {
        if (this.promptStatus != OutcomePromptStatus.PENDING) return;
        this.promptStatus = OutcomePromptStatus.DISMISSED;
        this.promptRespondedAt = now;
    }

    /** 자동 판정 안내 문구 (자동 판정이 없으면 null) */
    public String autoMessage() {
        return GoalOutcomeCalculator.autoMessage(autoResult, autoReason, metricPercent, routineRate, taskRate);
    }
}
