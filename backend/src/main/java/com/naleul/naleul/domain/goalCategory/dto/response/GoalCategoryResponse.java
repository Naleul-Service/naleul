package com.naleul.naleul.domain.goalCategory.dto.response;

import com.naleul.naleul.domain.generalCategory.dto.response.GeneralCategoryResponse;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalCategory.enums.GoalMode;
import com.naleul.naleul.domain.goalCategory.enums.GoalTopic;
import com.naleul.naleul.domain.goalCategory.kind.GoalSubType;
import com.naleul.naleul.domain.goalCategory.kind.GoalType;
import com.naleul.naleul.domain.milestone.entity.Milestone;
import com.naleul.naleul.domain.milestone.enums.MilestoneStatus;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.enums.RoutineStatus;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Builder(toBuilder = true)
public class GoalCategoryResponse {

    private Long goalCategoryId;
    private String goalCategoryName;
    private GoalCategoryStatus goalCategoryStatus;
    private LocalDate goalCategoryStartDate;
    private LocalDate goalCategoryEndDate;
    private String achievement;
    private String motive;
    private String colorCode;
    private List<GeneralCategoryInfo> generalCategories;

    // ── ✅ 추가: AI 목표 생성(BE-1) 컬럼 ─────────────────────────
    // 사용자가 직접 만든 기존 목표는 전부 null / 기본값으로 내려간다.
    private String emoji;
    private GoalTopic goalTopic;
    private String description;
    private String aiNote;
    /**
     * ⚠️ 필드 이름을 isAiGenerated 로 하지 않은 이유:
     * Lombok 이 boolean isXxx 필드의 getter 를 isXxx() 로 만들고,
     * Jackson 은 getter 이름에서 is 를 떼서 JSON 키를 "aiGenerated" 로 만든다.
     * 처음부터 aiGenerated 로 두면 Java 와 JSON 이름이 같아서 헷갈리지 않는다.
     */
    private boolean aiGenerated;
    /** Brain dump 에서 자동으로 만든 임시 목표 (종료일·마일스톤 없음) — 화면에서 "AI로 구체화하기"를 권해요 */
    private boolean temporary;
    /** 목표 형태 (V39): ACHIEVEMENT 달성형 / RECORD 기록형 (종료일·수치 없이 한 일을 쌓는 목표) */
    private GoalMode goalMode;
    /** 목표 카테고리 (명세 6.A.1.2). 예전 목표는 null */
    private GoalType goalType;
    private GoalSubType goalSubType;
    /** "기타"일 때 사용자가 적은 이름 */
    private String goalKindLabel;
    /** 화면 표시용 "건강 · 운동" */
    private String goalKindName;
    private String metricName;
    private String metricUnit;
    private BigDecimal startValue;
    private BigDecimal currentValue;
    private BigDecimal targetValue;
    /** 수치 목표 마감일 (V58). 따로 정하지 않았으면 null → 목표 종료일을 따른다 */
    private LocalDate metricTargetDate;
    private boolean deadlineFlexible;
    /**
     * 목록 조회(GET /goal-categories)에서만 채운다. 상세 조회에선 null.
     * lastActivityDate: 마지막 활동일 (Task 완료 · 수치 기록), 없으면 null
     * pausedDays: 진행 중인데 오래 활동이 없으면 멈춘 지 며칠째인지 (GoalPauseCalculator), 멈춤이 아니면 null
     */
    private LocalDate lastActivityDate;
    private Integer pausedDays;
    private PlanningStyle planningStyle;
    private List<MilestoneInfo> milestones;

    public static GoalCategoryResponse from(GoalCategory goalCategory) {
        return GoalCategoryResponse.builder()
                .goalCategoryId(goalCategory.getGoalCategoryId())
                .goalCategoryName(goalCategory.getGoalCategoryName())
                .goalCategoryStatus(goalCategory.getGoalCategoryStatus())
                .goalCategoryStartDate(goalCategory.getGoalCategoryStartDate())
                .goalCategoryEndDate(goalCategory.getGoalCategoryEndDate())
                .achievement(goalCategory.getAchievement())
                .motive(goalCategory.getMotive())
                .colorCode(goalCategory.getUserColor() != null
                        ? goalCategory.getUserColor().getColorCode()
                        : null)
                .generalCategories(goalCategory.getGeneralCategories() != null
                        ? goalCategory.getGeneralCategories().stream()
                          .map(GeneralCategoryInfo::from)
                          .toList()
                        : List.of())
                // ✅ 추가
                .emoji(goalCategory.getEmoji())
                .goalTopic(goalCategory.getGoalTopic())
                .description(goalCategory.getDescription())
                .aiNote(goalCategory.getAiNote())
                .aiGenerated(goalCategory.isAiGenerated())
                .temporary(goalCategory.isTemporary())
                .goalMode(goalCategory.getGoalMode())
                .goalType(goalCategory.getGoalType())
                .goalSubType(goalCategory.getGoalSubType())
                .goalKindLabel(goalCategory.getGoalKindLabel())
                .goalKindName(goalCategory.kind() != null ? goalCategory.kind().displayName() : null)
                .metricName(goalCategory.getMetricName())
                .metricUnit(goalCategory.getMetricUnit())
                .startValue(goalCategory.getStartValue())
                .currentValue(goalCategory.getCurrentValue())
                .targetValue(goalCategory.getTargetValue())
                .metricTargetDate(goalCategory.getMetricTargetDate())
                .deadlineFlexible(goalCategory.isDeadlineFlexible())
                .planningStyle(goalCategory.getPlanningStyle())
                // 엔티티에 @OrderBy("sortOrder ASC") 가 있어서 순서대로 나온다
                .milestones(goalCategory.getMilestones() != null
                        ? goalCategory.getMilestones().stream()
                          .map(MilestoneInfo::from)
                          .toList()
                        : List.of())
                .build();
    }

    @Getter
    @Builder
    public static class GeneralCategoryInfo {
        private Long generalCategoryId;
        private String generalCategoryName;
        private String colorCode;
        private LocalDate generalCategoryStartDate;
        private LocalDate generalCategoryEndDate;
        private GoalCategoryStatus generalCategoryStatus;
        private List<GeneralCategoryResponse.RoutineSummary> routines;
        /** 세부 목표의 실천 원칙 (AI 목표: 예 "하루 1,600kcal, 단백질 90g…"). 없으면 null */
        private String description;
        private String emoji;
        /** 목표의 "기타 할 일" 그릇 (영역을 안 고른 루틴·Task 가 들어감). 화면에서는 영역으로 보여주지 않는다 */
        private boolean defaultBucket;

        public static GeneralCategoryInfo from(GeneralCategory generalCategory) {
            return GeneralCategoryInfo.builder()
                    .generalCategoryId(generalCategory.getGeneralCategoryId())
                    .defaultBucket(generalCategory.isDefaultBucket())
                    .description(generalCategory.getDescription())
                    .emoji(generalCategory.getEmoji())
                    .generalCategoryName(generalCategory.getGeneralCategoryName())
                    .generalCategoryStartDate(generalCategory.getGeneralCategoryStartDate())
                    .generalCategoryEndDate(generalCategory.getGeneralCategoryEndDate())
                    .generalCategoryStatus(generalCategory.getGeneralCategoryStatus())
                    // DELETED 상태 루틴 제외
                    .routines(generalCategory.getRoutines().stream()
                            .filter(r -> r.getRoutineStatus() != RoutineStatus.DELETED)
                            .map(GeneralCategoryResponse.RoutineSummary::from)
                            .toList())
                    .colorCode(generalCategory.getColor() != null
                            ? generalCategory.getColor().getColorCode()
                            : null)
                    .build();
        }
    }

    /** ✅ 추가: 마일스톤 (수치 없는 목표면 targetValue = null) */
    @Getter
    @Builder
    public static class MilestoneInfo {
        private Long milestoneId;
        private String title;
        private String description;
        private LocalDate dueDate;
        private BigDecimal targetValue;
        /** 사용자가 수치를 직접 정했는지 (false = 시작값 → 목표값 직선에서 자동 계산, V40) */
        private boolean targetValueManual;
        private MilestoneStatus status;

        public static MilestoneInfo from(Milestone milestone) {
            return MilestoneInfo.builder()
                    .milestoneId(milestone.getMilestoneId())
                    .title(milestone.getTitle())
                    .description(milestone.getDescription())
                    .dueDate(milestone.getDueDate())
                    .targetValue(milestone.getTargetValue())
                    .targetValueManual(milestone.isTargetValueManual())
                    .status(milestone.getStatus())
                    .build();
        }
    }

    // 루틴 요약 정보 (기존 코드 그대로 — 실제로는 GeneralCategoryResponse.RoutineSummary 를 사용)
    @Getter
    @Builder
    public static class RoutineSummary {
        private Long routineId;
        private String routineName;
        private RoutineStatus routineStatus;
        private LocalDate repeatStartDate;
        private LocalDate repeatEndDate;
        private LocalTime repeatStartTime;
        private LocalTime repeatEndTime;
        private List<DayOfWeek> repeatDays;
        private boolean notificationEnabled;

        public static GeneralCategoryResponse.RoutineSummary from(Routine routine) {
            return GeneralCategoryResponse.RoutineSummary.builder()
                    .routineId(routine.getRoutineId())
                    .routineName(routine.getRoutineName())
                    .routineStatus(routine.getRoutineStatus())
                    .repeatStartDate(routine.getRepeatStartDate())
                    .repeatEndDate(routine.getRepeatEndDate())
                    .repeatStartTime(routine.getRepeatStartTime())
                    .repeatEndTime(routine.getRepeatEndTime())
                    .repeatDays(routine.getRepeatDays().stream()
                            .map(rd -> rd.getDayOfWeek())
                            .toList())
                    .notificationEnabled(routine.isNotificationEnabled())
                    .build();
        }
    }
}
