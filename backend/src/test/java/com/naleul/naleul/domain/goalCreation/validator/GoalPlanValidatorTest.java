package com.naleul.naleul.domain.goalCreation.validator;

import com.naleul.naleul.domain.generalCategory.enums.MetricComparator;
import com.naleul.naleul.domain.generalCategory.enums.MetricPeriod;
import com.naleul.naleul.domain.goalCategory.enums.GoalTopic;
import com.naleul.naleul.domain.goalCreation.client.FakeGoalAiClient;
import com.naleul.naleul.domain.goalCreation.client.PlanGenerationRequest;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.*;
import com.naleul.naleul.domain.goalCreation.dto.plan.PlanViolation;
import com.naleul.naleul.domain.goalCreation.dto.plan.TaskType;
import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.goalCreation.dto.slot.*;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static com.naleul.naleul.domain.goalCreation.dto.plan.Weekday.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * GoalPlanValidator 단위 테스트.
 * "모든 규칙을 통과하는 기본 초안(validPlan)"을 하나 만들어 두고,
 * 테스트마다 한 군데만 망가뜨려서 해당 규칙 ID가 정확히 잡히는지 확인한다.
 */
class GoalPlanValidatorTest {

    private final GoalPlanValidator validator = new GoalPlanValidator();

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);
    private static final LocalDate START = LocalDate.of(2026, 10, 3);
    private static final LocalDate END = LocalDate.of(2027, 3, 31);

    private static final PlanValidationContext PLANNER_CTX =
            new PlanValidationContext(TODAY, PlanningStyle.PLANNER, null, 300);

    // ───────────────────── 기본 초안 (명세 4-4 예시를 규칙에 맞게 고친 버전) ─────────────────────

    private static PlanGoal goal() {
        return new PlanGoal("몸무게 10KG 감량", "🏃", GoalTopic.HEALTH, "지방간 개선",
                new PlanGoalMetric("체중", "kg", bd(78), bd(68)), START, END, PlanningStyle.PLANNER, null);
    }

    private static List<PlanSubGoal> subGoals() {
        return List.of(
                new PlanSubGoal("sg1", "식단 관리", "🥗", null,
                        new PlanSubGoalMetric("칼로리", "kcal", MetricComparator.LTE, bd(1800), MetricPeriod.DAILY)),
                new PlanSubGoal("sg2", "운동 습관", "🏃", null,
                        new PlanSubGoalMetric("운동 횟수", "회", MetricComparator.GTE, bd(4), MetricPeriod.WEEKLY)),
                new PlanSubGoal("sg3", "생활 리듬", "🌙", null,
                        new PlanSubGoalMetric("수면", "시간", MetricComparator.GTE, bd(7), MetricPeriod.DAILY)));
    }

    private static List<PlanMilestone> milestones() {
        return List.of(
                new PlanMilestone("ms1", "적응기", null, LocalDate.of(2026, 10, 31), bd(76)),
                new PlanMilestone("ms2", "가속기", null, LocalDate.of(2026, 11, 30), bd(74)),
                new PlanMilestone("ms3", "정체기 돌파", null, LocalDate.of(2027, 1, 31), bd(71)),
                new PlanMilestone("ms4", "달성 & 유지", null, END, bd(68)));
    }

    private static List<PlanTask> tasks() {
        return List.of(
                routine("t1", "sg1", "아침 공복 체중 기록", 5, List.of(Weekday.values()), LocalTime.of(7, 30)),
                routine("t2", "sg2", "유산소 30분", 30, List.of(MON, WED, FRI, SAT), LocalTime.of(19, 30)),
                oneTime("t3", "sg1", "ms1", "인바디 측정하기", 20, LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4)),
                oneTime("t4", "sg1", "ms2", "식단표 점검하기", 30, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 25)),
                oneTime("t5", "sg2", "ms3", "운동 루틴 재조정", 30, LocalDate.of(2027, 1, 20), LocalDate.of(2027, 1, 25)),
                oneTime("t6", "sg3", "ms4", "유지 칼로리 계산하기", 20, LocalDate.of(2027, 3, 20), LocalDate.of(2027, 3, 25)));
    }

    private static GoalPlanDto validPlan() {
        return new GoalPlanDto(goal(), subGoals(), milestones(), tasks(), List.of());
    }

    private static PlanTask routine(String id, String sg, String title, int minutes, List<Weekday> days, LocalTime time) {
        return new PlanTask(id, TaskType.ROUTINE, sg, null, title, null, minutes, days, time, START, null, null, null);
    }

    private static PlanTask oneTime(String id, String sg, String ms, String title, int minutes, LocalDate scheduled, LocalDate due) {
        return new PlanTask(id, TaskType.ONE_TIME, sg, ms, title, null, minutes, null, null, null, null, scheduled, due);
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    /** 리스트의 i번째만 바꾼 새 리스트 */
    private static <T> List<T> replace(List<T> list, int index, UnaryOperator<T> change) {
        List<T> copy = new ArrayList<>(list);
        copy.set(index, change.apply(copy.get(index)));
        return copy;
    }

    private static GoalPlanDto withTasks(List<PlanTask> tasks) {
        return new GoalPlanDto(goal(), subGoals(), milestones(), tasks, List.of());
    }

    private static GoalPlanDto withMilestones(List<PlanMilestone> milestones) {
        return new GoalPlanDto(goal(), subGoals(), milestones, tasks(), List.of());
    }

    private PlanValidationResult validate(GoalPlanDto plan) {
        return validator.validate(plan, PLANNER_CTX);
    }

    private static List<String> ruleIds(List<PlanViolation> violations) {
        return violations.stream().map(PlanViolation::ruleId).toList();
    }

    // ───────────────────── 테스트 ─────────────────────

    @Test
    void 기본_초안은_HARD와_SOFT_위반이_모두_없다() {
        PlanValidationResult result = validate(validPlan());

        assertThat(result.hardViolations()).isEmpty();
        assertThat(result.softWarnings()).isEmpty();
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void FakeGoalAiClient가_만든_계획은_HARD_규칙을_통과한다() {
        // BE-2의 가짜 AI가 BE-5 개발 중 계속 실패 초안을 만들면 안 되므로 같이 검증
        GoalSlots slots = GoalSlots.empty()
                .withGoalStatement(Slot.filled("몸무게 10kg 감량"))
                .withMetric(Slot.filled(new MetricSlot("체중", bd(78), bd(68), "kg")))
                .withDeadline(Slot.filled(new DeadlineSlot(TODAY.plusMonths(6), false)));

        for (PlanningStyle style : PlanningStyle.values()) {
            GoalPlanDto plan = new FakeGoalAiClient().generatePlan(new PlanGenerationRequest(
                    slots, style, TODAY, 300, null, null, List.of(), null)).plan();

            PlanValidationResult result = validator.validate(plan,
                    new PlanValidationContext(TODAY, style, null, 300));

            assertThat(result.hardViolations()).as(style + " HARD 위반").isEmpty();
        }
    }

    @Nested
    @DisplayName("V1 목표")
    class Goal {

        @Test
        void 시작일이_오늘보다_이전이면_V1_1() {
            PlanGoal g = goal();
            PlanGoal past = new PlanGoal(g.title(), g.emoji(), g.category(), g.description(), g.metric(),
                    TODAY.minusDays(1), g.endDate(), g.planningStyle(), g.aiNote());

            assertThat(ruleIds(validate(new GoalPlanDto(past, subGoals(), milestones(), tasks(), null)).hardViolations()))
                    .contains("V1-1");
        }

        @Test
        void 기간이_2년을_넘으면_V1_1() {
            PlanGoal g = goal();
            PlanGoal tooLong = new PlanGoal(g.title(), g.emoji(), g.category(), g.description(), g.metric(),
                    START, START.plusYears(2).plusDays(1), g.planningStyle(), g.aiNote());

            assertThat(ruleIds(validate(new GoalPlanDto(tooLong, subGoals(), milestones(), tasks(), null)).hardViolations()))
                    .contains("V1-1");
        }

        @Test
        void 제목이_40자를_넘으면_V1_2() {
            PlanGoal g = goal();
            PlanGoal longTitle = new PlanGoal("가".repeat(41), g.emoji(), g.category(), g.description(), g.metric(),
                    g.startDate(), g.endDate(), g.planningStyle(), g.aiNote());

            PlanValidationResult result = validate(new GoalPlanDto(longTitle, subGoals(), milestones(), tasks(), null));

            assertThat(result.hardViolations()).extracting(PlanViolation::path).contains("goal.title");
        }

        private PlanGoal withMetric(PlanGoalMetric metric) {
            PlanGoal g = goal();
            return new PlanGoal(g.title(), g.emoji(), g.category(), g.description(), metric,
                    g.startDate(), g.endDate(), g.planningStyle(), g.aiNote());
        }

        @Test
        void 사용자가_말한_수치가_있는데_metric이_비면_V1_3() {
            PlanValidationContext said = new PlanValidationContext(TODAY, PlanningStyle.PLANNER, null, 300,
                    new MetricSlot("체중", bd(78), bd(68), "kg"));

            PlanValidationResult result = validator.validate(
                    new GoalPlanDto(withMetric(null), subGoals(), milestones(), tasks(), null), said);

            assertThat(result.hardViolations()).extracting(PlanViolation::path).contains("goal.metric");
        }

        @Test
        void 수치를_말하지_않은_목표는_metric이_없어도_V1_3_아님() {
            PlanValidationResult result = validate(new GoalPlanDto(withMetric(null), subGoals(), milestones(), tasks(), null));

            assertThat(ruleIds(result.hardViolations())).doesNotContain("V1-3");
        }

        @Test
        void metric_단위가_10자를_넘으면_V1_3() {
            PlanValidationResult result = validate(new GoalPlanDto(
                    withMetric(new PlanGoalMetric("체중", "킬로그램킬로그램킬로그램", bd(78), bd(68))),
                    subGoals(), milestones(), tasks(), null));

            assertThat(result.hardViolations()).extracting(PlanViolation::path).contains("goal.metric.unit");
        }

        @Test
        void 시작값과_목표값이_같으면_V1_3() {
            PlanValidationResult result = validate(new GoalPlanDto(
                    withMetric(new PlanGoalMetric("체중", "kg", bd(68), bd(68))),
                    subGoals(), milestones(), tasks(), null));

            assertThat(result.hardViolations()).extracting(PlanViolation::path).contains("goal.metric.targetValue");
        }
    }

    @Nested
    @DisplayName("V2 세부 목표")
    class SubGoal {

        @Test
        void AI_초안의_세부목표가_3개를_넘으면_V2_1_경고만_사용자는_5개까지() {
            List<PlanSubGoal> four = new ArrayList<>(subGoals());
            four.add(new PlanSubGoal("sg4", "생활", null, null,
                    new PlanSubGoalMetric("수면", "시간", MetricComparator.GTE, bd(7), MetricPeriod.DAILY)));
            GoalPlanDto plan = new GoalPlanDto(goal(), four, milestones(), tasks(), null);

            PlanValidationResult ai = validator.validate(plan,
                    new PlanValidationContext(TODAY, PlanningStyle.PLANNER, null, 300, null, true));
            // AI 권장 범위(1~3)를 넘어도 초안을 실패시키지 않는다
            assertThat(ruleIds(ai.hardViolations())).doesNotContain("V2-1");
            assertThat(ruleIds(ai.softWarnings())).contains("V2-1");
            assertThat(ruleIds(validate(plan).hardViolations())).doesNotContain("V2-1");
        }

        @Test
        void 영역이_하나뿐인_목표는_세부목표_1개도_된다() {
            List<PlanTask> onlySg1 = tasks().stream()
                    .map(t -> new PlanTask(t.tempId(), t.type(), "sg1", t.milestoneTempId(), t.title(), t.emoji(),
                            t.durationMinutes(), t.routineDays(), t.preferredStartTime(), t.startDate(), t.endDate(),
                            t.scheduledDate(), t.dueDate()))
                    .toList();
            GoalPlanDto plan = new GoalPlanDto(goal(), subGoals().subList(0, 1), milestones(), onlySg1, null);

            assertThat(ruleIds(validate(plan).hardViolations())).doesNotContain("V2-1");
        }

        @Test
        void 세부목표에_단위가_없으면_V2_1() {
            List<PlanSubGoal> broken = replace(subGoals(), 0, sg -> new PlanSubGoal(sg.tempId(), sg.title(), null, null,
                    new PlanSubGoalMetric("칼로리", " ", MetricComparator.LTE, bd(1800), MetricPeriod.DAILY)));

            PlanValidationResult result = validate(new GoalPlanDto(goal(), broken, milestones(), tasks(), null));

            assertThat(result.hardViolations()).extracting(PlanViolation::path).contains("subGoals[0].metric");
        }

        @Test
        void 세부목표_수치가_없어도_통과한다_업무처럼_잴_수_없는_영역() {
            List<PlanSubGoal> noMetric = replace(subGoals(), 0, sg -> new PlanSubGoal(sg.tempId(), sg.title(), null, null, null));

            PlanValidationResult result = validate(new GoalPlanDto(goal(), noMetric, milestones(), tasks(), null));

            assertThat(result.hardViolations()).extracting(PlanViolation::path).doesNotContain("subGoals[0].metric");
        }
    }

    @Nested
    @DisplayName("V3 마일스톤")
    class Milestone {

        @Test
        void 날짜가_오름차순이_아니면_V3_1() {
            List<PlanMilestone> broken = replace(milestones(), 1,
                    m -> new PlanMilestone(m.tempId(), m.title(), null, LocalDate.of(2026, 10, 20), m.targetValue()));

            assertThat(ruleIds(validate(withMilestones(broken)).hardViolations())).contains("V3-1");
        }

        @Test
        void 마지막_마일스톤이_목표_종료일과_다르면_V3_2() {
            List<PlanMilestone> broken = replace(milestones(), 3,
                    m -> new PlanMilestone(m.tempId(), m.title(), null, END.minusDays(1), m.targetValue()));

            assertThat(ruleIds(validate(withMilestones(broken)).hardViolations())).contains("V3-2");
        }

        @Test
        void 감량_목표인데_중간에_체중이_늘면_V3_3() {
            List<PlanMilestone> broken = replace(milestones(), 1,
                    m -> new PlanMilestone(m.tempId(), m.title(), null, m.dueDate(), bd(77)));   // 76 → 77

            assertThat(ruleIds(validate(withMilestones(broken)).hardViolations())).contains("V3-3");
        }

        @Test
        void 마지막_수치가_목표값과_다르면_V3_3() {
            List<PlanMilestone> broken = replace(milestones(), 3,
                    m -> new PlanMilestone(m.tempId(), m.title(), null, m.dueDate(), bd(69)));

            assertThat(ruleIds(validate(withMilestones(broken)).hardViolations())).contains("V3-3");
        }
    }

    @Nested
    @DisplayName("V4 참조/연결")
    class Reference {

        @Test
        void 없는_세부목표를_참조하면_V4_1() {
            List<PlanTask> broken = replace(tasks(), 0, t -> routine("t1", "sg999", t.title(), 5,
                    t.routineDays(), t.preferredStartTime()));

            assertThat(ruleIds(validate(withTasks(broken)).hardViolations())).contains("V4-1");
        }

        @Test
        void tempId가_중복되면_V4_1() {
            List<PlanTask> broken = replace(tasks(), 1, t -> routine("t1", t.subGoalTempId(), t.title(),
                    t.durationMinutes(), t.routineDays(), t.preferredStartTime()));

            assertThat(validate(withTasks(broken)).hardViolations())
                    .extracting(PlanViolation::path).contains("tasks[1].tempId");
        }

        @Test
        void 일회성_할일이_없는_마일스톤도_괜찮다_루틴_중심() {
            // 일회성 Task 를 전부 빼도 (마일스톤은 점검 시점일 뿐) HARD 위반이 없어야 한다
            List<PlanTask> routinesOnly = tasks().stream().filter(PlanTask::isRoutine).toList();

            assertThat(validate(withTasks(routinesOnly)).hardViolations()).isEmpty();
        }

        @Test
        void 할일이_없는_세부목표가_있으면_SOFT_V4_3() {
            // sg3의 유일한 할 일 t6를 sg1로 옮김
            List<PlanTask> moved = replace(tasks(), 5, t -> oneTime("t6", "sg1", "ms4", t.title(), 20,
                    t.scheduledDate(), t.dueDate()));

            PlanValidationResult result = validate(withTasks(moved));

            assertThat(result.isValid()).isTrue();
            assertThat(ruleIds(result.softWarnings())).containsExactly("V4-3");
        }

        @Test
        void 루틴이_하나도_없어도_실패는_아니고_AI_초안이면_V4_4_경고() {
            List<PlanTask> noRoutine = tasks().stream().filter(PlanTask::isOneTime).toList();

            assertThat(ruleIds(validate(withTasks(noRoutine)).hardViolations())).doesNotContain("V4-4");
            PlanValidationResult ai = validator.validate(withTasks(noRoutine),
                    new PlanValidationContext(TODAY, PlanningStyle.PLANNER, null, 300, null, true));
            assertThat(ruleIds(ai.hardViolations())).doesNotContain("V4-4");
            assertThat(ruleIds(ai.softWarnings())).contains("V4-4");
        }

        @Test
        void 할_일이_하나도_없으면_V4_4() {
            assertThat(ruleIds(validate(withTasks(List.of())).hardViolations())).contains("V4-4");
        }
    }

    @Nested
    @DisplayName("V5 할 일")
    class Task {

        @Test
        void AI_초안_루틴이_240분을_넘으면_V5_1_경고만_초안은_만든다() {
            List<PlanTask> longOne = replace(tasks(), 1, t -> routine("t2", "sg2", "집중 공부", 300,
                    List.of(MON), t.preferredStartTime()));

            PlanValidationResult ai = validator.validate(withTasks(longOne), aiDraft);
            assertThat(ruleIds(ai.hardViolations())).doesNotContain("V5-1");
            assertThat(ruleIds(ai.softWarnings())).contains("V5-1");
            assertThat(ruleIds(validate(withTasks(longOne)).hardViolations())).doesNotContain("V5-1");
        }

        @Test
        void 같은_활동을_시간만_바꾼_루틴은_중복_V5_8() {
            List<PlanTask> dup = new ArrayList<>(tasks());
            dup.add(routine("t9", "sg2", "유산소 45분", 25, List.of(MON, WED, FRI), null));
            dup.set(1, routine("t2", "sg2", "유산소 35분", 25, List.of(MON, WED, FRI), null));

            // AI 초안: 권장 규칙이라 경고만 (실제 중복은 GoalPlanRepairer 가 먼저 지운다)
            assertThat(ruleIds(validator.validate(withTasks(dup), aiDraft).hardViolations())).doesNotContain("V5-8");
            assertThat(ruleIds(validator.validate(withTasks(dup), aiDraft).softWarnings())).contains("V5-8");
            // 사용자 확정: 겹치면 경고만
            PlanValidationResult byUser = validate(withTasks(dup));
            assertThat(ruleIds(byUser.hardViolations())).doesNotContain("V5-8");
            assertThat(ruleIds(byUser.softWarnings())).contains("V5-8");
        }

        @Test
        void 활동_이름은_숫자와_단위를_빼고_비교한다() {
            assertThat(GoalPlanValidator.activityKey("유산소 35분")).isEqualTo(GoalPlanValidator.activityKey("유산소(45분)"));
            assertThat(GoalPlanValidator.activityKey("하체 근력 30분")).isNotEqualTo(GoalPlanValidator.activityKey("상체 근력 30분"));
        }

        @Test
        void AI_초안이라도_하루_24시간을_넘으면_V5_1_HARD() {
            List<PlanTask> tooLong = replace(tasks(), 1, t -> routine("t2", "sg2", "공부", 1500,
                    List.of(MON), t.preferredStartTime()));

            assertThat(ruleIds(validator.validate(withTasks(tooLong), aiDraft).hardViolations())).contains("V5-1");
        }

        @Test
        void AI_초안_자격증_공부_180분_루틴은_경고없이_된다() {
            List<PlanTask> study = replace(tasks(), 1, t -> routine("t2", "sg2", "이론 강의 4강", 180,
                    List.of(MON, WED, FRI), t.preferredStartTime()));

            PlanValidationResult ai = validator.validate(withTasks(study), aiDraft);
            // 이 180분 루틴(tasks[1]) 에 대한 V5-1 만 본다 — 다른 기본 Task 의 권장 범위 경고와 섞이지 않게
            assertThat(ai.hardViolations()).noneMatch(v -> v.ruleId().equals("V5-1") && v.path().startsWith("tasks[1]"));
            assertThat(ai.softWarnings()).noneMatch(v -> v.ruleId().equals("V5-1") && v.path().startsWith("tasks[1]"));
        }

        @Test
        void 운동_세션_60분_루틴은_된다() {
            List<PlanTask> gym = replace(tasks(), 1, t -> routine("t2", "sg2", "하체 근력 60분", 60,
                    List.of(MON, THU), t.preferredStartTime()));

            assertThat(ruleIds(validate(withTasks(gym)).hardViolations())).doesNotContain("V5-1");
        }

        private final PlanValidationContext aiDraft =
                new PlanValidationContext(TODAY, PlanningStyle.PLANNER, null, 300, null, true);

        @Test
        void AI_초안의_구매_준비_Task는_V5_6() {
            List<PlanTask> prep = replace(tasks(), 2, t -> oneTime(t.tempId(), t.subGoalTempId(), t.milestoneTempId(),
                    "저칼로리 식재료 구매하기", 20, t.scheduledDate(), t.dueDate()));

            assertThat(ruleIds(validator.validate(withTasks(prep), aiDraft).hardViolations())).doesNotContain("V5-6");
            assertThat(ruleIds(validator.validate(withTasks(prep), aiDraft).softWarnings())).contains("V5-6");
        }

        @Test
        void 사용자가_확정할_때_직접_적은_Task는_막지_않는다() {
            List<PlanTask> prep = replace(tasks(), 2, t -> oneTime(t.tempId(), t.subGoalTempId(), t.milestoneTempId(),
                    "운동화 구매하기", 20, t.scheduledDate(), t.dueDate()));

            assertThat(ruleIds(validate(withTasks(prep)).hardViolations())).doesNotContain("V5-6");
        }

        @Test
        void AI_초안_루틴에_방법_설명이_없으면_V5_7() {
            assertThat(ruleIds(validator.validate(validPlan(), aiDraft).hardViolations())).doesNotContain("V5-7");
            assertThat(ruleIds(validator.validate(validPlan(), aiDraft).softWarnings())).contains("V5-7");

            List<PlanTask> described = tasks().stream()
                    .map(t -> !t.isRoutine() ? t : new PlanTask(t.tempId(), t.type(), t.subGoalTempId(), null,
                            t.title(), t.emoji(), t.durationMinutes(), t.routineDays(), t.preferredStartTime(),
                            t.startDate(), t.endDate(), null, null, "스쿼트 4x10, 런지 3x12"))
                    .toList();
            assertThat(ruleIds(validator.validate(withTasks(described), aiDraft).softWarnings())).doesNotContain("V5-7");
        }

        @Test
        void 측정_접수_같은_진짜_할일은_준비_Task가_아니다() {
            assertThat(GoalPlanValidator.PREP_TASK.matcher("5km 기록 측정").find()).isFalse();
            assertThat(GoalPlanValidator.PREP_TASK.matcher("정보처리기사 필기 접수").find()).isFalse();
            assertThat(GoalPlanValidator.PREP_TASK.matcher("운동화 신발끈 조정하기").find()).isTrue();
            assertThat(GoalPlanValidator.PREP_TASK.matcher("스트레칭 루틴 영상 찾기").find()).isTrue();
            assertThat(GoalPlanValidator.PREP_TASK.matcher("저칼로리 식재료 구매하기").find()).isTrue();
        }

        @Test
        void 루틴에_요일이_없으면_V5_2() {
            List<PlanTask> broken = replace(tasks(), 0, t -> routine("t1", "sg1", t.title(), 5, List.of(), null));

            assertThat(ruleIds(validate(withTasks(broken)).hardViolations())).contains("V5-2");
        }

        @Test
        void 일회성에_마감일이_없으면_V5_2() {
            List<PlanTask> broken = replace(tasks(), 2, t -> oneTime("t3", "sg1", "ms1", t.title(), 20, null, null));

            assertThat(ruleIds(validate(withTasks(broken)).hardViolations())).contains("V5-2");
        }

        @Test
        void 마감일이_마일스톤_날짜보다_늦으면_V5_3() {
            List<PlanTask> broken = replace(tasks(), 2, t -> oneTime("t3", "sg1", "ms1", t.title(), 20,
                    LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2)));   // ms1 = 10/31

            assertThat(ruleIds(validate(withTasks(broken)).hardViolations())).contains("V5-3");
        }

        @Test
        void 실행일이_마감일보다_늦으면_V5_3() {
            List<PlanTask> broken = replace(tasks(), 2, t -> oneTime("t3", "sg1", "ms1", t.title(), 20,
                    LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 5)));

            assertThat(ruleIds(validate(withTasks(broken)).hardViolations())).contains("V5-3");
        }

        @Test
        void 명세_예시처럼_제목은_40분인데_소요시간이_30분이면_SOFT_V5_4() {
            List<PlanTask> sample = replace(tasks(), 1, t -> routine("t2", "sg2", "유산소 40분", 30,
                    t.routineDays(), t.preferredStartTime()));

            PlanValidationResult result = validate(withTasks(sample));

            assertThat(result.isValid()).isTrue();
            assertThat(ruleIds(result.softWarnings())).containsExactly("V5-4");
        }

        @Test
        void 제목_속_시간_표기를_분으로_계산한다() {
            assertThat(validator.minutesInTitle("유산소 40분")).isEqualTo(40);
            assertThat(validator.minutesInTitle("1시간 걷기")).isEqualTo(60);
            assertThat(validator.minutesInTitle("1시간 30분 독서")).isEqualTo(90);
            assertThat(validator.minutesInTitle("인바디 측정하기")).isNull();
        }

        @Test
        void 제목이_비어있으면_V5_5() {
            List<PlanTask> broken = replace(tasks(), 2, t -> oneTime("t3", "sg1", "ms1", " ", 20,
                    t.scheduledDate(), t.dueDate()));

            assertThat(ruleIds(validate(withTasks(broken)).hardViolations())).contains("V5-5");
        }
    }

    @Nested
    @DisplayName("V6 부하/선호")
    class Load {

        @Test
        void 루틴_주간_합계가_상한을_넘으면_V6_1_경고만() {
            // 30분 × 7일 = 210 + 기본 t1 35분 = 245분 → 상한 200으로 검증
            List<PlanTask> heavy = replace(tasks(), 1, t -> routine("t2", "sg2", "유산소 30분", 30,
                    List.of(Weekday.values()), t.preferredStartTime()));

            PlanValidationResult result = validator.validate(withTasks(heavy),
                    new PlanValidationContext(TODAY, PlanningStyle.PLANNER, null, 200, null, true));

            // AI 초안도 경고만 (권장 상한 때문에 초안 전체가 실패하지 않게)
            assertThat(ruleIds(result.hardViolations())).doesNotContain("V6-1");
            assertThat(ruleIds(result.softWarnings())).contains("V6-1");

            // 사용자가 직접 늘려서 확정할 때는 막지 않고 경고만
            PlanValidationResult byUser = validator.validate(withTasks(heavy),
                    new PlanValidationContext(TODAY, PlanningStyle.PLANNER, null, 200));
            assertThat(ruleIds(byUser.hardViolations())).doesNotContain("V6-1");
            assertThat(ruleIds(byUser.softWarnings())).contains("V6-1");
        }

        @Test
        void 선호_요일과_시간대를_벗어나면_SOFT_V6_2_V6_3() {
            PracticePreferenceSlot pref = new PracticePreferenceSlot(
                    List.of(MON, WED, FRI),
                    List.of(new TimeRange(LocalTime.of(19, 0), LocalTime.of(22, 0))),
                    null, null);

            PlanValidationResult result = validator.validate(validPlan(),
                    new PlanValidationContext(TODAY, PlanningStyle.PLANNER, pref, 300));

            assertThat(result.isValid()).isTrue();
            // t1: 매일 + 07:30 → 둘 다 위반, t2: 토요일 포함 → V6-2만
            assertThat(ruleIds(result.softWarnings())).containsExactlyInAnyOrder("V6-2", "V6-3", "V6-2");
        }
    }

    @Nested
    @DisplayName("V7 실행 성향별 배치")
    class Placement {

        @Test
        void 계획형인데_같은날_일회성이_3개면_SOFT_V7_1() {
            LocalDate sameDay = LocalDate.of(2026, 10, 10);
            List<PlanTask> crowded = new ArrayList<>(tasks());
            crowded.add(oneTime("t7", "sg1", "ms1", "장보기", 30, sameDay, sameDay));
            crowded.add(oneTime("t8", "sg2", "ms1", "운동화 사기", 30, sameDay, sameDay));
            crowded.add(oneTime("t9", "sg3", "ms1", "수면 앱 설치", 10, sameDay, sameDay));

            assertThat(ruleIds(validate(withTasks(crowded)).softWarnings())).contains("V7-1");
        }

        @Test
        void 즉흥형인데_일회성이_구간_마지막에_모여있지_않으면_SOFT_V7_2() {
            PlanValidationResult result = validator.validate(validPlan(),
                    new PlanValidationContext(TODAY, PlanningStyle.SPONTANEOUS, null, 300));

            assertThat(ruleIds(result.softWarnings())).contains("V7-2");
        }

        @Test
        void 즉흥형이고_일회성이_구간_마지막_3일_안에_있으면_통과() {
            List<PlanTask> lastMinute = List.of(
                    tasks().get(0), tasks().get(1),
                    oneTime("t3", "sg1", "ms1", "인바디 측정하기", 20, LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31)),
                    oneTime("t4", "sg1", "ms2", "식단표 점검하기", 30, LocalDate.of(2026, 11, 29), LocalDate.of(2026, 11, 30)),
                    oneTime("t5", "sg2", "ms3", "운동 루틴 재조정", 30, LocalDate.of(2027, 1, 30), LocalDate.of(2027, 1, 31)),
                    oneTime("t6", "sg3", "ms4", "유지 칼로리 계산하기", 20, LocalDate.of(2027, 3, 30), END));

            PlanValidationResult result = validator.validate(withTasks(lastMinute),
                    new PlanValidationContext(TODAY, PlanningStyle.SPONTANEOUS, null, 300));

            assertThat(result.softWarnings()).isEmpty();
        }
    }
}
