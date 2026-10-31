package com.naleul.naleul.domain.goalCreation.validator;

import com.naleul.naleul.domain.goalCreation.personal.CapacityProfile;
import com.naleul.naleul.domain.goalCreation.personal.PersonalPlanContext;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.*;
import com.naleul.naleul.domain.goalCreation.dto.plan.PlanViolation;
import com.naleul.naleul.domain.goalCreation.dto.plan.TaskType;
import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.goalCreation.dto.slot.MetricSlot;
import com.naleul.naleul.domain.goalCreation.dto.slot.PracticePreferenceSlot;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 목표 초안 검증 (명세 4-5).
 *
 * 쓰이는 곳
 *  - 초안 생성 직후(BE-5): HARD 위반이면 위반 목록을 AI에 보내 1회 수정 요청
 *  - 확정 요청(BE-6): 사용자가 고친 초안이 HARD 위반이면 422 + violations
 *
 * 규칙 ID(V1-1 등)는 명세와 1:1로 맞춰서, 로그나 iOS 화면에서 어떤 규칙에 걸렸는지 바로 찾을 수 있게 했다.
 * message는 사용자에게 그대로 보여줄 수 있는 문장으로 쓴다(확정 422 응답에 그대로 나감).
 *
 * DB·스프링 의존이 없는 순수 로직이라 단위 테스트로 규칙을 전부 검증할 수 있다.
 */
@Component
public class GoalPlanValidator {

    // ── 개수 제약 ──
    // 루틴 중심 · 최소 실천 원칙: 사용자가 할 일이 적을수록 실제로 지킨다.
    // 일회성 Task 는 0개가 기본 (시험 접수·모의고사·측정처럼 날짜가 정해진 일만), 마일스톤은 점검 시점 1~4개.
    //
    // ✅ 원칙: AI 초안의 "품질 규칙"은 전부 권장(SOFT)이다. 규칙 때문에 목표 생성이 실패하면 안 된다.
    //  - SOFT(권장): AI 선호 개수·시간(AI_LIMITS), 주간 상한(V6-1), 준비용 할 일(V5-6),
    //               루틴 방법 설명(V5-7), 같은 활동 루틴 중복(V5-8)
    //  - HARD(저장 불가): 날짜 없음/순서 뒤집힘, 끊어진 연결, 사용자 한도(USER_LIMITS) 초과처럼
    //               그대로 저장하면 데이터가 깨지는 것만. 이것도 대부분 GoalPlanRepairer 가 먼저 고친다.
    record Limits(int subGoalMin, int subGoalMax, int milestoneMin, int milestoneMax,
                  int routineMin, int routineMax, int oneTimeMin, int oneTimeMax,
                  int routineMinutesMin, int routineMinutesMax, int oneTimeMinutesMin, int oneTimeMinutesMax) {
    }

    // V52: AI 초안은 세부 목표 1개 · 마일스톤 0개 (GoalDraftWorker.simplify 가 정리한 뒤 검증한다)
    // 2026-10: 루틴 길이 상한 30분 → 240분. "이론 30강을 5일에 하루 15분" 같은 비현실적인 계획을 막는다 (필요 분량 그대로)
    static final Limits AI_LIMITS = new Limits(1, 1, 0, 0, 1, 6, 0, 5, 10, 240, 5, 240);
    // 루틴 최소 0개: 강의 수강처럼 일회성 할 일만으로 된 계획도 실패시키지 않는다 (AI 는 1개 이상을 "권장"으로 경고)
    // V52: 마일스톤 최소 0개 — 마일스톤 없이 만드는 것이 기본. 예전 앱에서 사용자가 직접 넣은 마일스톤은 최대 8개까지 그대로 받는다
    // 2026-10: 사용자가 직접 고치는 루틴도 최대 8시간까지 (예전 4시간)
    static final Limits USER_LIMITS = new Limits(1, 5, 0, 8, 0, 10, 0, 30, 1, 24 * 60, 1, 24 * 60); // 2026-10 직접 수정은 길이 제한 없음 (하루 안)

    static Limits limits(PlanValidationContext ctx) {
        return ctx.strictContent() ? AI_LIMITS : USER_LIMITS;
    }

    /**
     * 개수·시간 범위 검사.
     * - 사용자 한도(USER_LIMITS)를 벗어나면 HARD → 저장 불가
     * - AI 생성(strictContent)인데 AI 선호 범위(AI_LIMITS)만 벗어나면 SOFT → 경고만 남기고 초안은 만든다
     *
     * ⚠️ AI 선호 범위를 HARD 로 두면, AI 가 수정 요청 후에도 35분 루틴 하나만 만들어도
     *    초안 전체가 FAILED("계획을 만들지 못했어요")가 된다. 5~30분은 "권장"이지 필수가 아니다.
     */
    private static void checkRange(Collector c, PlanValidationContext ctx, String ruleId, String path, Integer value,
                                   int aiMin, int aiMax, int userMin, int userMax, String label, String suffix) {
        if (value == null || value < userMin || value > userMax) {
            c.hard(ruleId, path, label + " " + userMin + "~" + userMax + suffix + "로 설정해 주세요.");
            return;
        }
        if (ctx.strictContent() && (value < aiMin || value > aiMax)) {
            c.soft(ruleId, path, label + " " + aiMin + "~" + aiMax + suffix + "를 권장해요.");
        }
    }

    // ── 시간·길이 제약 ──
    static final int TITLE_MAX = 40;
    static final int METRIC_NAME_MAX = 30;   // goal_category.metric_name 길이
    static final int METRIC_UNIT_MAX = 10;   // goal_category.metric_unit 길이
    static final int ROUTINE_DESCRIPTION_MAX = 300;   // routine.description 길이
    static final int MAX_GOAL_YEARS = 2;
    static final int PLANNER_SAME_DAY_MAX = 2;
    static final double SPONTANEOUS_LAST_DAYS_RATIO = 0.7;
    static final int SPONTANEOUS_LAST_DAYS = 3;

    /** "유산소 40분", "1시간 30분 걷기" 같은 제목 속 시간 표기 */
    private static final Pattern HOUR = Pattern.compile("(\\d+)\\s*시간");
    private static final Pattern MINUTE = Pattern.compile("(\\d+)\\s*분");

    /**
     * V5-6 준비·쇼핑·검색 Task 금지.
     * "운동화 끈 조정하기", "저칼로리 식재료 구매하기", "스트레칭 영상 찾기"처럼 목표 효과는 없고 할 일만 늘리는 Task.
     * (시험 "접수", 기록 "측정"은 진짜 해야 하는 일이라 허용)
     */
    public static final Pattern PREP_TASK = Pattern.compile(
            "구매|구입|장보기|장 보기|쇼핑|사러 가기|주문하기|찾기|찾아보기|검색|알아보기|다운로드|설치|앱 깔기|가입하기"
                    + "|준비물|끈 조정|계획 세우기|계획 짜기|목표 적기|목록 만들기|리스트 만들기");

    public PlanValidationResult validate(GoalPlanDto plan, PlanValidationContext ctx) {
        Collector c = new Collector();
        if (plan == null || plan.goal() == null) {
            c.hard("V1-2", "goal", "목표 정보가 비어 있어요.");
            return c.result();
        }

        validateGoal(plan.goal(), ctx, c);
        validateSubGoals(plan, ctx, c);
        validateMilestones(plan, ctx, c);
        validateReferences(plan, ctx, c);
        validateTasks(plan, ctx, c);
        validateDuplicateRoutines(plan, ctx, c);
        validateLoad(plan, ctx, c);
        validatePlacement(plan, ctx, c);
        return c.result();
    }

    // ───────────────────── V1: Goal ─────────────────────

    private void validateGoal(PlanGoal goal, PlanValidationContext ctx, Collector c) {
        // V1-2 제목 1~40자
        if (isBlank(goal.title()) || goal.title().strip().length() > TITLE_MAX) {
            c.hard("V1-2", "goal.title", "목표 이름은 1~40자로 입력해 주세요.");
        }

        // V1-1 기간
        LocalDate start = goal.startDate(), end = goal.endDate();
        if (start == null || end == null) {
            c.hard("V1-1", "goal.startDate", "목표 시작일과 종료일이 필요해요.");
            return;
        }
        if (start.isBefore(ctx.today())) {
            c.hard("V1-1", "goal.startDate", "시작일은 오늘 이후여야 해요.");
        }
        if (!end.isAfter(start)) {
            c.hard("V1-1", "goal.endDate", "종료일은 시작일보다 뒤여야 해요.");
        }
        if (end.isAfter(start.plusYears(MAX_GOAL_YEARS))) {
            c.hard("V1-1", "goal.endDate", "목표 기간은 최대 2년까지 설정할 수 있어요.");
        }
        validateGoalMetric(goal, ctx, c);
    }

    /**
     * V1-3 목표 수치 — 목표 상세의 진행 도표(시작값 → 목표값)와 매일 기록이 이 값으로 그려진다.
     *  - 사용자가 인터뷰에서 현재·목표 수치를 말했는데 goal.metric 이 비면 안 된다.
     *  - 채웠다면 이름(30자)·단위(10자)는 DB 길이 안, 시작값·목표값 모두 있고 서로 달라야 한다.
     */
    private void validateGoalMetric(PlanGoal goal, PlanValidationContext ctx, Collector c) {
        PlanGoalMetric m = goal.metric();
        MetricSlot said = ctx.metric();
        if (m == null) {
            if (said != null && said.currentValue() != null && said.targetValue() != null) {
                c.hard("V1-3", "goal.metric", "말씀하신 현재·목표 수치가 계획에 빠졌어요.");
            }
            return;
        }
        if (isBlank(m.name()) || m.name().strip().length() > METRIC_NAME_MAX) {
            c.hard("V1-3", "goal.metric.name", "목표 수치 이름은 1~" + METRIC_NAME_MAX + "자로 입력해 주세요.");
        }
        if (isBlank(m.unit()) || m.unit().strip().length() > METRIC_UNIT_MAX) {
            c.hard("V1-3", "goal.metric.unit", "목표 수치 단위는 1~" + METRIC_UNIT_MAX + "자로 입력해 주세요.");
        }
        if (m.startValue() == null || m.targetValue() == null) {
            c.hard("V1-3", "goal.metric", "목표 수치에는 시작값과 목표값이 모두 필요해요.");
        } else if (m.startValue().compareTo(m.targetValue()) == 0) {
            c.hard("V1-3", "goal.metric.targetValue", "목표값이 시작값과 같아요.");
        }
    }

    // ───────────────────── V2: SubGoal ─────────────────────

    private void validateSubGoals(GoalPlanDto plan, PlanValidationContext ctx, Collector c) {
        List<PlanSubGoal> subGoals = plan.subGoals();
        checkRange(c, ctx, "V2-1", "subGoals", subGoals.size(),
                AI_LIMITS.subGoalMin(), AI_LIMITS.subGoalMax(), USER_LIMITS.subGoalMin(), USER_LIMITS.subGoalMax(),
                "세부 목표는", "개");

        for (int i = 0; i < subGoals.size(); i++) {
            PlanSubGoal sg = subGoals.get(i);
            String path = "subGoals[" + i + "]";
            if (isBlank(sg.title()) || sg.title().strip().length() > TITLE_MAX) {
                c.hard("V2-1", path + ".title", "세부 목표 이름은 1~40자로 입력해 주세요.");
            }
            // 세부 목표 수치는 선택: 회사 업무·공부 영역처럼 숫자로 재기 어려운 영역에 수치를 강제하면
            // AI 가 "업무 효율 20% 향상" 같은 잴 수 없는 숫자를 지어낸다. 채웠다면 값과 단위는 다 있어야 한다.
            if (sg.metric() != null && (sg.metric().targetValue() == null || isBlank(sg.metric().unit()))) {
                c.hard("V2-1", path + ".metric", "세부 목표 수치를 쓰려면 목표 수치와 단위가 모두 필요해요.");
            }
        }
    }

    // ───────────────────── V3: Milestone ─────────────────────

    private void validateMilestones(GoalPlanDto plan, PlanValidationContext ctx, Collector c) {
        List<PlanMilestone> milestones = plan.milestones();
        PlanGoal goal = plan.goal();

        checkRange(c, ctx, "V3-1", "milestones", milestones.size(),
                AI_LIMITS.milestoneMin(), AI_LIMITS.milestoneMax(), USER_LIMITS.milestoneMin(), USER_LIMITS.milestoneMax(),
                "마일스톤은", "개");

        LocalDate prev = null;
        for (int i = 0; i < milestones.size(); i++) {
            PlanMilestone ms = milestones.get(i);
            String path = "milestones[" + i + "]";
            if (isBlank(ms.title()) || ms.title().strip().length() > TITLE_MAX) {
                c.hard("V3-1", path + ".title", "마일스톤 이름은 1~40자로 입력해 주세요.");
            }
            if (ms.dueDate() == null) {
                c.hard("V3-1", path + ".dueDate", "마일스톤 날짜가 필요해요.");
                continue;
            }
            if (prev != null && !ms.dueDate().isAfter(prev)) {
                c.hard("V3-1", path + ".dueDate", "마일스톤 날짜는 앞 마일스톤보다 뒤여야 해요.");
            }
            if (goal.startDate() != null && goal.endDate() != null
                    && (ms.dueDate().isBefore(goal.startDate()) || ms.dueDate().isAfter(goal.endDate()))) {
                c.hard("V3-1", path + ".dueDate", "마일스톤 날짜는 목표 기간 안에 있어야 해요.");
            }
            prev = ms.dueDate();
        }

        // V3-2 마지막 마일스톤 = 목표 종료일
        if (!milestones.isEmpty() && goal.endDate() != null) {
            PlanMilestone last = milestones.get(milestones.size() - 1);
            if (last.dueDate() != null && !last.dueDate().equals(goal.endDate())) {
                c.hard("V3-2", "milestones[" + (milestones.size() - 1) + "].dueDate",
                        "마지막 마일스톤 날짜는 목표 종료일과 같아야 해요.");
            }
        }

        validateMilestoneValues(goal, milestones, c);
    }

    /** V3-3 수치가 있는 목표면 마일스톤 수치가 시작값→목표값 방향으로만 움직이고, 마지막 = 목표값 */
    private void validateMilestoneValues(PlanGoal goal, List<PlanMilestone> milestones, Collector c) {
        PlanGoalMetric metric = goal.metric();
        if (metric == null || metric.startValue() == null || metric.targetValue() == null || milestones.isEmpty()) {
            return;
        }
        int direction = metric.targetValue().compareTo(metric.startValue());   // -1 감소형(감량), 1 증가형
        BigDecimal prev = metric.startValue();

        for (int i = 0; i < milestones.size(); i++) {
            BigDecimal value = milestones.get(i).targetValue();
            String path = "milestones[" + i + "].targetValue";
            if (value == null) {
                c.hard("V3-3", path, "마일스톤마다 그 시점의 목표 수치가 필요해요.");
                return;
            }
            boolean wrongDirection = direction != 0 && Integer.signum(value.compareTo(prev)) == -direction;
            boolean overshoot = direction != 0 && Integer.signum(value.compareTo(metric.targetValue())) == direction;
            if (wrongDirection || overshoot) {
                c.hard("V3-3", path, "마일스톤 수치는 시작값에서 목표값 방향으로만 바뀌어야 해요.");
                return;
            }
            prev = value;
        }

        BigDecimal last = milestones.get(milestones.size() - 1).targetValue();
        if (last.compareTo(metric.targetValue()) != 0) {
            c.hard("V3-3", "milestones[" + (milestones.size() - 1) + "].targetValue",
                    "마지막 마일스톤 수치는 최종 목표값과 같아야 해요.");
        }
    }

    // ───────────────────── V4: 참조 / 연결 ─────────────────────

    private void validateReferences(GoalPlanDto plan, PlanValidationContext ctx, Collector c) {
        Set<String> subGoalIds = uniqueIds(plan.subGoals(), PlanSubGoal::tempId, "subGoals", c);
        Set<String> milestoneIds = uniqueIds(plan.milestones(), PlanMilestone::tempId, "milestones", c);
        uniqueIds(plan.tasks(), PlanTask::tempId, "tasks", c);

        List<PlanTask> tasks = plan.tasks();
        for (int i = 0; i < tasks.size(); i++) {
            PlanTask t = tasks.get(i);
            String path = "tasks[" + i + "]";
            // V4-1 참조 존재
            if (t.subGoalTempId() == null || !subGoalIds.contains(t.subGoalTempId())) {
                c.hard("V4-1", path + ".subGoalTempId", "할 일이 연결된 세부 목표를 찾을 수 없어요.");
            }
            if (t.milestoneTempId() != null && !milestoneIds.contains(t.milestoneTempId())) {
                c.hard("V4-1", path + ".milestoneTempId", "할 일이 연결된 마일스톤을 찾을 수 없어요.");
            }
        }

        // (V4-2 "마일스톤마다 일회성 할 일 1개 이상"은 없앰 — 마일스톤은 점검 시점이고, 억지로 채운 일회성 Task 가
        //  "운동화 끈 조정하기" 같은 쓸모없는 할 일을 만들었다)

        // V4-3 (SOFT) 모든 세부 목표에 할 일 1개 이상
        Set<String> coveredSubGoals = tasks.stream()
                .map(PlanTask::subGoalTempId).filter(Objects::nonNull).collect(Collectors.toSet());
        for (int i = 0; i < plan.subGoals().size(); i++) {
            String id = plan.subGoals().get(i).tempId();
            if (id != null && !coveredSubGoals.contains(id)) {
                c.soft("V4-3", "subGoals[" + i + "]", "할 일이 없는 세부 목표가 있어요.");
            }
        }

        // V4-4 할 일 개수 (명세 4-2 표. 4-5 표에는 ID가 없어 V4-4로 부여)
        long routines = tasks.stream().filter(PlanTask::isRoutine).count();
        long oneTimes = tasks.stream().filter(PlanTask::isOneTime).count();
        if (tasks.isEmpty()) {
            c.hard("V4-4", "tasks", "할 일을 하나 이상 만들어 주세요.");
        }
        checkRange(c, ctx, "V4-4", "tasks", (int) routines,
                AI_LIMITS.routineMin(), AI_LIMITS.routineMax(), USER_LIMITS.routineMin(), USER_LIMITS.routineMax(),
                "반복 루틴은", "개");
        checkRange(c, ctx, "V4-4", "tasks", (int) oneTimes,
                AI_LIMITS.oneTimeMin(), AI_LIMITS.oneTimeMax(), USER_LIMITS.oneTimeMin(), USER_LIMITS.oneTimeMax(),
                "일회성 할 일은", "개");
    }

    /** tempId 누락/중복도 참조 오류(V4-1)로 본다. 중복이면 어느 쪽에 연결할지 알 수 없기 때문 */
    private <T> Set<String> uniqueIds(List<T> items, Function<T, String> idOf, String name, Collector c) {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            String id = idOf.apply(items.get(i));
            if (isBlank(id) || !ids.add(id)) {
                c.hard("V4-1", name + "[" + i + "].tempId", "항목 식별자가 비어 있거나 중복돼요.");
            }
        }
        return ids;
    }

    // ───────────────────── V5: Task ─────────────────────

    private void validateTasks(GoalPlanDto plan, PlanValidationContext ctx, Collector c) {
        Map<String, PlanMilestone> milestoneById = plan.milestones().stream()
                .filter(m -> m.tempId() != null)
                .collect(Collectors.toMap(PlanMilestone::tempId, m -> m, (a, b) -> a));
        PlanGoal goal = plan.goal();

        List<PlanTask> tasks = plan.tasks();
        for (int i = 0; i < tasks.size(); i++) {
            PlanTask t = tasks.get(i);
            String path = "tasks[" + i + "]";

            // V5-5 제목
            if (isBlank(t.title()) || t.title().strip().length() > TITLE_MAX) {
                c.hard("V5-5", path + ".title", "할 일 이름은 1~40자로 입력해 주세요.");
            }
            if (t.type() == null) {
                c.hard("V5-2", path + ".type", "할 일 종류(반복/일회성)가 필요해요.");
                continue;
            }

            // V5-1 소요 시간
            if (t.isRoutine()) {
                checkRange(c, ctx, "V5-1", path + ".durationMinutes", t.durationMinutes(),
                        AI_LIMITS.routineMinutesMin(), AI_LIMITS.routineMinutesMax(),
                        USER_LIMITS.routineMinutesMin(), USER_LIMITS.routineMinutesMax(), "루틴은", "분");
            } else {
                checkRange(c, ctx, "V5-1", path + ".durationMinutes", t.durationMinutes(),
                        AI_LIMITS.oneTimeMinutesMin(), AI_LIMITS.oneTimeMinutesMax(),
                        USER_LIMITS.oneTimeMinutesMin(), USER_LIMITS.oneTimeMinutesMax(), "일회성 할 일은", "분");
            }

            // V5-6 준비·쇼핑·검색 Task 금지 (AI 생성 때만 — 사용자가 초안에서 직접 적은 건 존중)
            if (ctx.strictContent() && t.title() != null && PREP_TASK.matcher(t.title()).find()) {
                c.soft("V5-6", path + ".title", "'" + t.title().strip()
                        + "' 같은 준비·구매·검색 할 일은 만들지 않아요. 목표에 직접 효과가 있는 실천만 남겨 주세요.");
            }

            // V5-4 (SOFT) 제목 속 시간과 소요 시간 일치
            Integer minutesInTitle = minutesInTitle(t.title());
            if (minutesInTitle != null && t.durationMinutes() != null && !minutesInTitle.equals(t.durationMinutes())) {
                c.soft("V5-4", path + ".durationMinutes",
                        "'" + t.title() + "'의 제목과 소요 시간(" + t.durationMinutes() + "분)이 달라요.");
            }

            if (t.isRoutine()) {
                validateRoutine(t, path, goal, ctx, c);
            } else {
                validateOneTime(t, path, goal, milestoneById, ctx, c);
            }
        }
    }

    /**
     * V5-8 같은 활동 루틴 중복 금지.
     * "유산소 35분 월·수·금" + "유산소 45분 월·수·금"처럼 이름만 숫자가 다른 루틴은 사용자 눈엔 같은 일이 두 번이다.
     *  - AI 초안: 같은 활동은 루틴 하나로 (강도를 올리는 계획은 description 에 "3주차부터 45분" 처럼) → HARD
     *  - 사용자가 확정: 기간·요일이 실제로 겹칠 때만 경고 (단계별로 나눈 건 본인 선택)
     */
    private void validateDuplicateRoutines(GoalPlanDto plan, PlanValidationContext ctx, Collector c) {
        List<PlanTask> tasks = plan.tasks();
        PlanGoal goal = plan.goal();
        for (int i = 0; i < tasks.size(); i++) {
            PlanTask a = tasks.get(i);
            if (!a.isRoutine() || a.title() == null) continue;
            for (int j = i + 1; j < tasks.size(); j++) {
                PlanTask b = tasks.get(j);
                if (!b.isRoutine() || b.title() == null) continue;
                if (!activityKey(a.title()).equals(activityKey(b.title())) || activityKey(a.title()).isEmpty()) continue;

                String message = "'" + a.title().strip() + "'와(과) '" + b.title().strip()
                        + "'은(는) 같은 활동이에요. 루틴 하나로 합쳐 주세요 (강도를 올린다면 방법 설명에 적어 주세요).";
                if (ctx.strictContent() || overlaps(a, b, goal)) {
                    c.soft("V5-8", "tasks[" + j + "]", message);
                }
            }
        }
    }

    /** "유산소 35분" / "유산소 45 분" / "유산소(45분)" → "유산소" */
    public static String activityKey(String title) {
        return title.replaceAll("\\d+\\s*(분|시간|회|세트|개|km|KM|k)?", "")
                .replaceAll("[\\s()\\[\\]·,./~\\-]", "")
                .toLowerCase();
    }

    /** 기간과 요일이 하루라도 겹치는지 */
    private boolean overlaps(PlanTask a, PlanTask b, PlanGoal goal) {
        LocalDate aStart = a.startDate() != null ? a.startDate() : goal.startDate();
        LocalDate aEnd = a.endDate() != null ? a.endDate() : goal.endDate();
        LocalDate bStart = b.startDate() != null ? b.startDate() : goal.startDate();
        LocalDate bEnd = b.endDate() != null ? b.endDate() : goal.endDate();
        if (aStart != null && bEnd != null && aStart.isAfter(bEnd)) return false;
        if (bStart != null && aEnd != null && bStart.isAfter(aEnd)) return false;
        if (a.routineDays() == null || b.routineDays() == null) return true;
        return a.routineDays().stream().filter(Objects::nonNull).anyMatch(b.routineDays()::contains);
    }

    private void validateRoutine(PlanTask t, String path, PlanGoal goal, PlanValidationContext ctx, Collector c) {
        // V5-7 루틴에는 구체적인 방법(무엇을·얼마나·어떻게)이 있어야 한다 — 제목만으론 따라 할 수 없다.
        // 비어 있는지는 AI 생성 때만 본다 (확정 요청에서 예전 앱이 description 을 빼고 보내도 막지 않게)
        if (isBlank(t.description())) {
            if (ctx.strictContent()) {
                c.soft("V5-7", path + ".description", "'" + t.title() + "' 루틴에 구체적인 방법(무엇을·얼마나·어떻게)을 적어 주세요.");
            }
        } else if (t.description().strip().length() > ROUTINE_DESCRIPTION_MAX) {
            c.hard("V5-7", path + ".description", "루틴 설명은 " + ROUTINE_DESCRIPTION_MAX + "자 이하로 줄여 주세요.");
        }
        // V5-2 요일 1개 이상
        if (t.routineDays() == null || t.routineDays().stream().noneMatch(Objects::nonNull)) {
            c.hard("V5-2", path + ".routineDays", "루틴은 요일을 1개 이상 골라 주세요.");
        }
        // 루틴 기간이 목표 기간을 벗어나지 않게 (명세 4-4 필드 규칙)
        if (t.startDate() != null && goal.startDate() != null && t.startDate().isBefore(goal.startDate())) {
            c.hard("V5-2", path + ".startDate", "루틴 시작일은 목표 시작일 이후여야 해요.");
        }
        if (t.endDate() != null && goal.endDate() != null && t.endDate().isAfter(goal.endDate())) {
            c.hard("V5-2", path + ".endDate", "루틴 종료일은 목표 종료일 이전이어야 해요.");
        }
        if (t.startDate() != null && t.endDate() != null && t.endDate().isBefore(t.startDate())) {
            c.hard("V5-2", path + ".endDate", "루틴 종료일은 시작일보다 뒤여야 해요.");
        }
    }

    private void validateOneTime(PlanTask t, String path, PlanGoal goal,
                                 Map<String, PlanMilestone> milestoneById, PlanValidationContext ctx, Collector c) {
        // V5-2 필수값
        if (t.dueDate() == null) {
            c.hard("V5-2", path + ".dueDate", "일회성 할 일에는 마감일이 필요해요.");
        }
        // V52: 마일스톤이 없어도 된다 — 일회성 할 일은 자기 마감일만으로 충분하다 (연결했다면 아래 V5-3 으로 날짜만 확인)
        if (t.dueDate() == null) return;

        // V5-3 scheduledDate ≤ dueDate ≤ milestone.dueDate, 그리고 목표 시작일 이후
        if (t.scheduledDate() != null && t.scheduledDate().isAfter(t.dueDate())) {
            c.hard("V5-3", path + ".scheduledDate", "실행일은 마감일보다 늦을 수 없어요.");
        }
        LocalDate earliest = t.scheduledDate() != null ? t.scheduledDate() : t.dueDate();
        LocalDate lowerBound = goal.startDate() != null ? goal.startDate() : ctx.today();
        if (earliest.isBefore(lowerBound)) {
            c.hard("V5-3", path + (t.scheduledDate() != null ? ".scheduledDate" : ".dueDate"),
                    "할 일 날짜는 목표 시작일 이후여야 해요.");
        }
        PlanMilestone ms = t.milestoneTempId() != null ? milestoneById.get(t.milestoneTempId()) : null;
        if (ms != null && ms.dueDate() != null && t.dueDate().isAfter(ms.dueDate())) {
            c.hard("V5-3", path + ".dueDate", "할 일 마감일은 연결된 마일스톤 날짜보다 늦을 수 없어요.");
        }
    }

    /** "40분" → 40, "1시간" → 60, "1시간 30분" → 90, 표기 없으면 null */
    Integer minutesInTitle(String title) {
        if (title == null) return null;
        Matcher h = HOUR.matcher(title);
        Matcher m = MINUTE.matcher(title);
        boolean hasHour = h.find(), hasMinute = m.find();
        if (!hasHour && !hasMinute) return null;
        int total = 0;
        if (hasHour) total += Integer.parseInt(h.group(1)) * 60;
        if (hasMinute) total += Integer.parseInt(m.group(1));
        return total;
    }

    // ───────────────────── V6: 부하 / 선호 ─────────────────────

    private void validateLoad(GoalPlanDto plan, PlanValidationContext ctx, Collector c) {
        // V6-1 루틴 주간 합계
        int weeklyMinutes = plan.tasks().stream()
                .filter(PlanTask::isRoutine)
                .filter(t -> t.durationMinutes() != null && t.routineDays() != null)
                .mapToInt(t -> t.durationMinutes() * (int) t.routineDays().stream().filter(Objects::nonNull).distinct().count())
                .sum();
        if (weeklyMinutes > ctx.maxWeeklyRoutineMinutes()) {
            String message = "루틴이 주 " + weeklyMinutes + "분이에요. 주 " + ctx.maxWeeklyRoutineMinutes() + "분을 넘으면 지치기 쉬워요.";
            // 2026-10: 주간 상한은 언제나 "권장"(경고만). 예전엔 AI 초안에서 HARD 라 AI 가 루틴을 15분씩으로 쪼그라뜨렸다.
            // 목표를 이루는 데 필요한 분량이 우선 — 넘기면 AI 가 aiNote 에 이유를 적는다.
            c.soft("V6-1", "tasks", message);
        }
        validatePersonal(plan, ctx, c);

        PracticePreferenceSlot pref = ctx.preference();
        if (pref == null) return;

        List<PlanTask> tasks = plan.tasks();
        for (int i = 0; i < tasks.size(); i++) {
            PlanTask t = tasks.get(i);
            if (!t.isRoutine()) continue;
            String path = "tasks[" + i + "]";

            // V6-2 (SOFT) 루틴 요일 ⊆ 선호 요일
            if (pref.preferredDays() != null && !pref.preferredDays().isEmpty() && t.routineDays() != null) {
                Set<Weekday> preferred = new HashSet<>(pref.preferredDays());   // EnumSet.copyOf는 빈 컬렉션이면 예외라 HashSet 사용
                if (!preferred.containsAll(t.routineDays().stream().filter(Objects::nonNull).toList())) {
                    c.soft("V6-2", path + ".routineDays", "'" + t.title() + "'이(가) 선호 요일이 아닌 날에도 있어요.");
                }
            }
            // V6-3 (SOFT) 시작 시각이 선호 시간대 안
            if (pref.preferredTimeRanges() != null && !pref.preferredTimeRanges().isEmpty()
                    && t.preferredStartTime() != null
                    && pref.preferredTimeRanges().stream().noneMatch(r -> r.contains(t.preferredStartTime()))) {
                c.soft("V6-3", path + ".preferredStartTime", "'" + t.title() + "'의 시작 시각이 선호 시간대 밖이에요.");
            }
        }
    }

    // ───────────────────── V8: 개인 실행 기록 (SOFT, V47) ─────────────────────

    /** 이미 진행 중인 루틴과 합쳐 이 개수를 넘으면 경고 */
    static final int TOTAL_ROUTINE_WARN = 8;

    private void validatePersonal(GoalPlanDto plan, PlanValidationContext ctx, Collector c) {
        PersonalPlanContext personal = ctx.personal();
        if (personal == null) return;
        CapacityProfile p = personal.profile();
        List<PlanTask> tasks = plan.tasks();

        // V8-1 자주 놓치는 시간대에 시작하는 루틴
        if (p.weak() != null) {
            for (int i = 0; i < tasks.size(); i++) {
                PlanTask t = tasks.get(i);
                if (!t.isRoutine() || t.preferredStartTime() == null || t.routineDays() == null) continue;
                int h = t.preferredStartTime().getHour();
                boolean inWeak = h >= p.weak().startHour() && h < p.weak().endHour()
                        && t.routineDays().stream().filter(Objects::nonNull)
                        .anyMatch(d -> p.weak().dayGroup().contains(d.toDayOfWeek()));
                if (inWeak) {
                    c.soft("V8-1", "tasks[" + i + "].preferredStartTime", "'" + t.title() + "'이(가) 평소 자주 놓치는 시간("
                            + p.weak().dayGroup().label() + " " + p.weak().startHour() + "–" + p.weak().endHour() + "시)에 있어요.");
                }
            }
        }

        // V8-2 이미 진행 중인 루틴과 합치면 너무 많음
        long newRoutines = tasks.stream().filter(PlanTask::isRoutine).count();
        if (p.activeRoutineCount() > 0 && p.activeRoutineCount() + newRoutines > TOTAL_ROUTINE_WARN) {
            c.soft("V8-2", "tasks", "이미 진행 중인 루틴이 " + p.activeRoutineCount() + "개 있어서, 합치면 "
                    + (p.activeRoutineCount() + newRoutines) + "개가 돼요. 꼭 필요한 루틴만 남겨 보세요.");
        }
    }

    // ───────────────────── V7: 실행 성향별 배치 (SOFT) ─────────────────────

    private void validatePlacement(GoalPlanDto plan, PlanValidationContext ctx, Collector c) {
        List<PlanTask> oneTimes = plan.tasks().stream()
                .filter(PlanTask::isOneTime)
                .filter(t -> placementDate(t) != null)
                .toList();
        if (oneTimes.isEmpty() || ctx.planningStyle() == null) return;

        if (ctx.planningStyle() == PlanningStyle.PLANNER) {
            // V7-1 같은 날 일회성 최대 2개
            Map<LocalDate, Long> perDay = oneTimes.stream()
                    .collect(Collectors.groupingBy(this::placementDate, TreeMap::new, Collectors.counting()));
            perDay.forEach((date, count) -> {
                if (count > PLANNER_SAME_DAY_MAX) {
                    c.soft("V7-1", "tasks", date + "에 일회성 할 일이 " + count + "개 몰려 있어요.");
                }
            });
            return;
        }

        // V7-2 SPONTANEOUS: 70% 이상이 소속 구간의 마지막 3일 안
        Map<String, LocalDate> milestoneDue = plan.milestones().stream()
                .filter(m -> m.tempId() != null && m.dueDate() != null)
                .collect(Collectors.toMap(PlanMilestone::tempId, PlanMilestone::dueDate, (a, b) -> a));
        long inLastDays = oneTimes.stream()
                .filter(t -> {
                    LocalDate due = milestoneDue.get(t.milestoneTempId());
                    return due != null && ChronoUnit.DAYS.between(placementDate(t), due) < SPONTANEOUS_LAST_DAYS;
                })
                .count();
        if ((double) inLastDays / oneTimes.size() < SPONTANEOUS_LAST_DAYS_RATIO) {
            c.soft("V7-2", "tasks", "즉흥형 계획인데 일회성 할 일이 구간 마지막 며칠에 충분히 모여 있지 않아요.");
        }
    }

    /** 배치 판단 기준일: 권장 실행일이 있으면 그것, 없으면 마감일 */
    private LocalDate placementDate(PlanTask t) {
        return t.scheduledDate() != null ? t.scheduledDate() : t.dueDate();
    }

    // ───────────────────── 내부 ─────────────────────

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 위반을 HARD/SOFT로 나눠 모으는 작은 도우미 (검증 1회마다 새로 만들어서 스레드 안전) */
    private static final class Collector {
        private final List<PlanViolation> hard = new ArrayList<>();
        private final List<PlanViolation> soft = new ArrayList<>();

        void hard(String ruleId, String path, String message) {
            hard.add(new PlanViolation(ruleId, path, message));
        }

        void soft(String ruleId, String path, String message) {
            soft.add(new PlanViolation(ruleId, path, message));
        }

        PlanValidationResult result() {
            return new PlanValidationResult(List.copyOf(hard), List.copyOf(soft));
        }
    }
}
