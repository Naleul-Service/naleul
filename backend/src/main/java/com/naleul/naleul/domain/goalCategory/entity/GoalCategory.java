package com.naleul.naleul.domain.goalCategory.entity;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalCategory.enums.GoalMode;
import com.naleul.naleul.domain.goalCategory.enums.GoalTopic;
import com.naleul.naleul.domain.goalCategory.kind.GoalKind;
import com.naleul.naleul.domain.goalCategory.kind.GoalSubType;
import com.naleul.naleul.domain.goalCategory.kind.GoalType;
import com.naleul.naleul.domain.milestone.entity.Milestone;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "goal_category")
public class GoalCategory {

    // ── PK ────────────────────────────────────────────────
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long goalCategoryId;

    // ── FK (연관관계) ───────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "color_id")
    private UserColor userColor;

    @OneToMany(mappedBy = "goalCategory", fetch = FetchType.LAZY)
    private List<GeneralCategory> generalCategories = new ArrayList<>();

    @OneToMany(mappedBy = "goalCategory", fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<Milestone> milestones = new ArrayList<>();

    // ── 일반 컬럼 ───────────────────────────────────────────
    @Column(nullable = false)
    private String goalCategoryName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoalCategoryStatus goalCategoryStatus;

    private String motive;
    private LocalDate goalCategoryStartDate;
    private LocalDate goalCategoryEndDate;
    private String achievement;
    private String achievementImageUrl;

    // ── AI 목표 생성용 컬럼 (BE-1) ─────────────────────────
    // 기존 사용자가 직접 만든 목표는 전부 null / 기본값으로 남는다.
    private String emoji;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalTopic goalTopic = GoalTopic.ETC;

    @Column(length = 500)
    private String description;

    @Column(length = 500)
    private String aiNote;

    @Column(nullable = false)
    private boolean isAiGenerated = false;

    @Column(length = 30)
    private String metricName;

    @Column(length = 10)
    private String metricUnit;

    @Column(precision = 10, scale = 2)
    private BigDecimal startValue;

    @Column(precision = 10, scale = 2)
    private BigDecimal currentValue;

    @Column(precision = 10, scale = 2)
    private BigDecimal targetValue;

    /**
     * 수치 목표의 마감일 (V58, 2026-10). null 이면 목표 종료일을 따른다.
     * 목표 종료일과 따로 움직인다 — 목표 기간을 바꿔도 수치 마감은 그대로, 수치 마감만 따로 바꿀 수 있다.
     */
    @Column(name = "metric_target_date")
    private LocalDate metricTargetDate;

    @Column(nullable = false)
    private boolean isDeadlineFlexible = false;

    /**
     * 생성 당시 실행 성향 스냅샷 (나중에 사용자 성향이 바뀌어도 이 목표는 그대로)
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PlanningStyle planningStyle;

    // ── TimeBlock 명세(3) — 임시 목표 (V34) ─────────────────
    /**
     * Brain dump Task 가 기존 목표와 맞지 않을 때 AI 가 만든 임시 목표.
     * - 종료일 없음(null) → 자동 만료 스케줄러 대상이 아님 (findExpiredCategories 가 end < today 비교라 NULL 은 걸리지 않음)
     * - Free 목표 개수 한도(2개)에서 제외 (countActiveByUserId)
     * - "목표 구체화하기"로 정식 목표가 되면 promote()
     */
    @Column(nullable = false)
    private boolean isTemporary = false;

    /** V69: 목표를 만든 시각 (Aha-moment · 운영 대시보드). 그 전 목표는 null */
    @org.hibernate.annotations.CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private java.time.LocalDateTime createdAt;

    // ── 목표 형태 (V39) ─────────────────────────────────────
    /**
     * ACHIEVEMENT(달성형, 기본) / RECORD(기록형).
     * 기록형은 회사 업무처럼 "한 일을 쌓아 두는" 목표라 종료일·수치·마일스톤이 없다 (임시 목표와 같은 모양이지만
     * "구체화"할 대상이 아니라 그 자체로 완성된 목표). Free 목표 개수 한도에는 포함된다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalMode goalMode = GoalMode.ACHIEVEMENT;

    // ── 목표 카테고리 (명세 6.A.1.2, V37) ─────────────────────
    /** 1단계: 건강 / 학습·자기개발 / 생활습관 / 기타. 예전 목표는 null */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private GoalType goalType;

    /** 2단계: 운동 / 다이어트 / 언어 / 기상 … */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private GoalSubType goalSubType;

    /** "기타"일 때 사용자가 적은 이름 (공백 포함 최대 10자) */
    @Column(length = 10)
    private String goalKindLabel;

    // ── 생성자 (빌더) ───────────────────────────────────────
    @Builder(access = AccessLevel.PRIVATE)
    public GoalCategory(Long goalCategoryId, User user, UserColor userColor,
                        String goalCategoryName, GoalCategoryStatus goalCategoryStatus,
                        LocalDate goalCategoryStartDate, LocalDate goalCategoryEndDate, String motive) {
        this.goalCategoryId = goalCategoryId;
        this.user = user;
        this.userColor = userColor;
        this.goalCategoryName = goalCategoryName;
        this.goalCategoryStatus = goalCategoryStatus;
        this.motive = motive;
        this.goalCategoryStartDate = goalCategoryStartDate;
        this.goalCategoryEndDate = goalCategoryEndDate;
        this.generalCategories = new ArrayList<>();
        this.milestones = new ArrayList<>();
        this.goalTopic = GoalTopic.ETC;
    }

    // ── create ────────────────────────────────────────────

    public static GoalCategory create(User user, UserColor userColor, String name,
                                      LocalDate startDate, LocalDate endDate, String motive) {
        GoalCategoryStatus initialStatus = calculateStatusByStartDate(startDate);

        return GoalCategory.builder()
                .user(user)
                .userColor(userColor)
                .goalCategoryName(name)
                .goalCategoryStatus(initialStatus)
                .goalCategoryStartDate(startDate)
                .goalCategoryEndDate(endDate)
                .motive(motive)
                .build();
    }

    /**
     * Brain dump 임시 목표 (명세 3 · 3-2).
     * 오늘 시작(IN_PROGRESS), 종료일 없음. 마일스톤·지표는 "목표 구체화하기"에서 채운다.
     */
    public static GoalCategory createTemporary(User user, UserColor userColor, String name,
                                               String emoji, LocalDate today) {
        GoalCategory goal = create(user, userColor, name, today, null, null);
        goal.emoji = emoji;
        goal.isTemporary = true;
        return goal;
    }

    private static GoalCategoryStatus calculateStatusByStartDate(LocalDate startDate) {
        if (startDate != null && startDate.isAfter(LocalDate.now())) {
            return GoalCategoryStatus.NOT_STARTED;
        }
        return GoalCategoryStatus.IN_PROGRESS;
    }

    /**
     * AI 초안 확정 시 호출. create()로 기본 목표를 만든 뒤 AI 전용 값을 채운다.
     * currentValue는 시작값으로 초기화 (명세 5-2).
     */
    public void applyAiPlan(String emoji, GoalTopic goalTopic, String description, String aiNote,
                            String metricName, String metricUnit,
                            BigDecimal startValue, BigDecimal targetValue,
                            boolean isDeadlineFlexible, PlanningStyle planningStyle) {
        this.emoji = emoji;
        this.goalTopic = goalTopic != null ? goalTopic : GoalTopic.ETC;
        this.description = description;
        this.aiNote = aiNote;
        this.metricName = metricName;
        this.metricUnit = metricUnit;
        this.startValue = startValue;
        this.currentValue = startValue;
        this.targetValue = targetValue;
        this.isDeadlineFlexible = isDeadlineFlexible;
        this.planningStyle = planningStyle;
        this.isAiGenerated = true;
    }

    /** 목표 카테고리 지정. 기존 goal_topic(통계·iOS 호환)도 같이 맞춘다. null 이면 그대로 */
    public void applyKind(GoalKind kind) {
        if (kind == null) return;
        this.goalType = kind.type();
        this.goalSubType = kind.subType();
        this.goalKindLabel = kind.customLabel();
        this.goalTopic = kind.type().getTopic();
    }

    public GoalKind kind() {
        return GoalKind.restore(goalType, goalSubType, goalKindLabel);
    }

    /**
     * 기록형 목표 (V39). 오늘 시작(IN_PROGRESS), 종료일·수치 없음.
     * 회사 업무처럼 달성 기준이 없는 일을 이름만으로 바로 만든다.
     */
    public static GoalCategory createRecord(User user, UserColor userColor, String name, String emoji, String motive,
                                            LocalDate today) {
        GoalCategory goal = create(user, userColor, name, today, null, motive);
        goal.emoji = emoji;
        goal.goalMode = GoalMode.RECORD;
        return goal;
    }

    public boolean isRecord() {
        return goalMode == GoalMode.RECORD;
    }

    /** 기본 생활 습관 그릇 (V52) */
    public boolean isHabit() {
        return goalMode == GoalMode.HABIT;
    }

    /** 목표형(수치·기간이 있는 목표). 예전 목표는 goal_mode 기본값이 ACHIEVEMENT */
    public boolean isAchievement() {
        return goalMode == null || goalMode == GoalMode.ACHIEVEMENT;
    }

    /**
     * 기본 생활 습관 그릇 (V52). 사용자마다 1개, 처음 필요할 때 HabitGoalProvisioner 가 만든다.
     * 오늘 시작(IN_PROGRESS), 종료일·수치 없음. 실제 습관은 habit 테이블에 담긴다.
     */
    public static GoalCategory createHabitHome(User user, UserColor userColor, String name, String emoji,
                                               LocalDate today) {
        GoalCategory goal = create(user, userColor, name, today, null, null);
        goal.emoji = emoji;
        goal.goalMode = GoalMode.HABIT;
        goal.goalType = GoalType.HABIT;
        goal.goalTopic = GoalType.HABIT.getTopic();
        return goal;
    }

    /**
     * 종료일 정하기 · 지우기. clear = true 면 지운다.
     * V53: 업무형은 전체 마감이 없어서 업무형 수정 때 항상 clear 로 부른다 (마감은 주제마다).
     */
    public void changeDeadline(LocalDate deadline, boolean clear) {
        if (clear) {
            this.goalCategoryEndDate = null;
        } else if (deadline != null) {
            this.goalCategoryEndDate = deadline;
        }
    }

    /**
     * 임시 목표 → 기록형 목표로 그대로 두기.
     * 브레인덤프가 만든 "나를 업무" 같은 임시 목표는 구체화(AI 설계)할 필요 없이 기록형이 맞는 경우가 많다.
     */
    public void keepAsRecord() {
        this.isTemporary = false;
        this.goalMode = GoalMode.RECORD;
        this.goalCategoryEndDate = null;
    }

    /**
     * AI 없이 직접 만든 예전 목표 (2026-10 이전 앱). 목표 상세에서 AI 로 다시 설계할 수 있다.
     * 확정하면 새 목표를 만들지 않고 이 목표에 계획(수치 · 세부 목표 · 마일스톤 · 루틴)을 채운다.
     */
    public boolean isLegacyManual() {
        return !isTemporary && !isAiGenerated && isAchievement()
                && goalCategoryStatus != GoalCategoryStatus.COMPLETED
                && goalCategoryStatus != GoalCategoryStatus.DELETED;
    }

    /** 임시 목표 → 정식 목표 (목표 구체화 완료 시) */
    public void promote() {
        this.isTemporary = false;
    }

    public void updateCurrentValue(BigDecimal currentValue) {
        this.currentValue = currentValue;
    }

    // ── update ────────────────────────────────────────────

    public void update(String name, LocalDate startDate, LocalDate endDate, String motive) {
        if (name != null) {
            this.goalCategoryName = name;
        }
        if (motive != null) {
            this.motive = motive;
        }

        LocalDate newStartDate = startDate != null ? startDate : this.goalCategoryStartDate;

        if (startDate != null) {
            this.goalCategoryStartDate = startDate;
        }
        if (endDate != null) {
            this.goalCategoryEndDate = endDate;
        }

        // ✅ 아직 진행 흐름 중인 상태(NOT_STARTED, IN_PROGRESS)일 때만 시작일 기준으로 재계산
        //    COMPLETED, DELETED는 이미 확정된 상태이므로 날짜 수정으로 되돌아가지 않음
        if (this.goalCategoryStatus == GoalCategoryStatus.NOT_STARTED
                || this.goalCategoryStatus == GoalCategoryStatus.IN_PROGRESS) {
            this.goalCategoryStatus = calculateStatusByStartDate(newStartDate);
        }
    }

    /**
     * 목표 상세 화면에서 바꾸는 표시·수치 정보. null 이면 그대로, 문자열이 빈 값이면 지운다.
     */
    public void updateDetails(String emoji, String description, String metricName, String metricUnit,
                              BigDecimal startValue, BigDecimal currentValue, BigDecimal targetValue) {
        if (emoji != null) this.emoji = emoji.isBlank() ? null : emoji.strip();
        if (description != null) this.description = description.isBlank() ? null : description.strip();
        if (metricName != null) this.metricName = metricName.isBlank() ? null : metricName.strip();
        if (metricUnit != null) this.metricUnit = metricUnit.isBlank() ? null : metricUnit.strip();
        if (startValue != null) this.startValue = startValue;
        if (currentValue != null) this.currentValue = currentValue;
        if (targetValue != null) this.targetValue = targetValue;
    }

    /** 수치 목표 마감일 — 지정 안 했으면 목표 종료일 */
    public LocalDate effectiveMetricTargetDate() {
        return metricTargetDate != null ? metricTargetDate : goalCategoryEndDate;
    }

    /**
     * 수치 마감일 바꾸기. clear 면 다시 "목표 종료일 따라가기"
     */
    public void changeMetricTargetDate(LocalDate date, boolean clear) {
        if (clear) this.metricTargetDate = null;
        else if (date != null) this.metricTargetDate = date;
    }

    /**
     * 목표 종료일을 바꾸기 직전에 부른다: 수치 마감을 따로 정하지 않았던 목표면 지금 날짜로 고정해서
     * 목표 기간을 바꿔도 수치 마감이 같이 움직이지 않게 한다 (2026-10 "각각 독립").
     */
    public void pinMetricTargetDateBeforeEndChange(LocalDate newEnd) {
        if (metricTargetDate == null && targetValue != null && goalCategoryEndDate != null
                && newEnd != null && !newEnd.equals(goalCategoryEndDate)) {
            this.metricTargetDate = goalCategoryEndDate;
        }
    }

    /** 수치 목표 지우기 ("수치 없이 진행하기") */
    public void clearMetric() {
        this.metricTargetDate = null;
        this.metricName = null;
        this.metricUnit = null;
        this.startValue = null;
        this.currentValue = null;
        this.targetValue = null;
    }

    public void updateColor(UserColor color) {
        this.userColor = color;
    }


    /**
     * 시작일이 도래해 자동으로 진행 상태로 전환. NOT_STARTED가 아니면 아무 일도 하지 않음.
     */
    public void start() {
        if (this.goalCategoryStatus == GoalCategoryStatus.NOT_STARTED) {
            this.goalCategoryStatus = GoalCategoryStatus.IN_PROGRESS;
        }
    }

    /**
     * 시스템에 의한 자동 완료 처리. achievement는 채우지 않고 상태만 전환한다.
     */
    public void expire() {
        if (this.goalCategoryStatus == GoalCategoryStatus.IN_PROGRESS) {
            this.goalCategoryStatus = GoalCategoryStatus.COMPLETED;
        }
    }

    /**
     * 사용자가 직접 달성 내용을 입력하며 완료 처리. IN_PROGRESS 또는 NOT_STARTED 상태에서만 가능.
     */
    public void complete(LocalDate endDate, String achievement, String achievementImageUrl) {
        if (this.goalCategoryStatus == GoalCategoryStatus.DELETED) {
            throw new CustomException(ErrorCode.GOAL_CATEGORY_ALREADY_DELETED);
        }
        this.goalCategoryEndDate = endDate;
        this.achievement = achievement;
        this.achievementImageUrl = achievementImageUrl;
        this.goalCategoryStatus = GoalCategoryStatus.COMPLETED;
    }

    /**
     * 자동 만료된 목표에 사용자가 나중에 achievement를 채워 넣는 경우
     */
    public void fillAchievement(String achievement, String achievementImageUrl) {
        if (this.goalCategoryStatus != GoalCategoryStatus.COMPLETED) {
            throw new CustomException(ErrorCode.GOAL_CATEGORY_NOT_COMPLETED);
        }
        this.achievement = achievement;
        this.achievementImageUrl = achievementImageUrl;
    }

    // ── delete ────────────────────────────────────────────

    /**
     * 소프트 삭제 처리.
     * 삭제 시 색상 참조를 해제해 UserColor 독립 삭제가 가능하도록 함.
     */
    public void delete() {
        this.goalCategoryStatus = GoalCategoryStatus.DELETED;
        this.userColor = null;
    }
}
