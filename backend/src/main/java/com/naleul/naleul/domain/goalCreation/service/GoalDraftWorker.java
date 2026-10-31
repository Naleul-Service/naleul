package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.goalCreation.personal.PersonalPlanContext;
import com.naleul.naleul.domain.goalCreation.personal.PlanFitBuilder;
import com.naleul.naleul.domain.goalCreation.client.AiCallMeta;
import com.naleul.naleul.domain.goalCreation.client.GoalAiClient;
import com.naleul.naleul.domain.goalCreation.client.GoalAiException;
import com.naleul.naleul.domain.goalCreation.client.PlanGenerationRequest;
import com.naleul.naleul.domain.goalCreation.client.PlanGenerationResult;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanGoal;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanGoalMetric;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanSubGoal;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanTask;
import com.naleul.naleul.domain.goalCreation.dto.plan.PlanViolation;
import com.naleul.naleul.domain.goalCreation.service.GoalDraftTxService.GenerationJob;
import com.naleul.naleul.domain.goalCreation.validator.GoalPlanValidator;
import com.naleul.naleul.domain.goalCreation.validator.PlanValidationResult;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import com.naleul.naleul.domain.goalCreation.dto.slot.DeadlineSlot;
import com.naleul.naleul.domain.goalCreation.dto.slot.GoalSlots;
import com.naleul.naleul.domain.goalCreation.dto.slot.MetricSlot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 초안 생성 본 작업. goalPlanExecutor 스레드에서 실행된다 (요청 스레드 X, 트랜잭션 X).
 *
 * 흐름 (명세 4-5 재시도 정책, 8-4)
 *  1. AI 계획 생성
 *  2. 검증 → HARD 위반이면 위반 목록을 붙여 AI에 1회 수정 요청 → 다시 검증
 *  3. 그래도 HARD 위반이 남으면 → 위반이 더 적은 쪽 계획을 골라, 남은 위반을 경고로 붙여 READY
 *     (사용자가 초안 검토 화면에서 바로 고친다. 확정할 때는 사용자 기준으로 다시 검증한다)
 *     FAILED(AI_PLAN_INVALID)는 목표 날짜가 없거나 할 일이 하나도 없는 "쓸 수 없는" 계획일 때만
 *  4. 통과하면 SOFT 경고를 warnings에 담아 READY
 *  → AI 호출은 최대 2회
 *
 * ⚠️ 이 빈은 싱글톤이고 스레드 2개가 동시에 쓴다. 그래서 작업 중간값을 필드에 저장하지 않고
 *    전부 지역 변수/반환값(Outcome)으로만 주고받는다. 필드에 담으면 두 작업의 값이 서로 섞인다.
 *
 * 어떤 예외가 나도 초안을 GENERATING으로 남겨두지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoalDraftWorker {

    static final String AI_PLAN_INVALID = "AI_PLAN_INVALID";

    private final GoalAiClient aiClient;
    private final GoalPlanValidator validator;
    private final GoalDraftTxService txService;
    /** 루틴 시각을 고정 시간 · 다른 루틴과 겹치지 않게 맞춤 (2026-10). 단위 테스트처럼 없으면 AI 시각 그대로 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RoutineTimeFitter routineTimeFitter;

    /** 검증까지 끝난 결과. plan == null이면 수정 요청 후에도 HARD 위반 */
    record Outcome(GoalPlanDto plan, AiCallMeta meta) {
    }

    public void generate(GenerationJob job) {
        long started = System.currentTimeMillis();
        try {
            Outcome outcome = generateValidPlan(job);
            if (outcome.plan() == null) {
                txService.failDraft(job.draftId(), AI_PLAN_INVALID);
                log.warn("[GoalDraft] HARD 위반이 남아 실패 draftId={} elapsedMs={}", job.draftId(), elapsed(started));
                return;
            }
            txService.completeDraft(job.draftId(), withFit(fitTimes(outcome.plan(), job), job), outcome.meta());
            log.info("[GoalDraft] 생성 완료 draftId={} version={} warnings={} model={} inputTokens={} outputTokens={} elapsedMs={}",
                    job.draftId(), job.version(), outcome.plan().warnings().size(),
                    outcome.meta() != null ? outcome.meta().model() : null,
                    outcome.meta() != null ? outcome.meta().inputTokens() : null,
                    outcome.meta() != null ? outcome.meta().outputTokens() : null,
                    elapsed(started));
        } catch (GoalAiException e) {
            log.error("[GoalDraft] AI 호출 실패 draftId={} reason={} elapsedMs={}",
                    job.draftId(), e.getReason(), elapsed(started), e);
            txService.failDraft(job.draftId(), e.getReason().name());
        } catch (RuntimeException e) {
            log.error("[GoalDraft] 예상치 못한 실패 draftId={}", job.draftId(), e);
            txService.failDraft(job.draftId(), GoalAiException.Reason.AI_UNAVAILABLE.name());
        }
    }

    Outcome generateValidPlan(GenerationJob job) {
        PlanGenerationRequest request = job.aiRequest();

        PlanGenerationResult first = aiClient.generatePlan(request);
        LocalDate today = job.validationContext().today();
        GoalPlanDto plan = pinUserFacts(normalize(first.plan(), request.planningStyle(), today), request.slots(), today);
        PlanValidationResult result = validator.validate(plan, job.validationContext());

        if (result.isValid()) {
            return new Outcome(plan.withWarnings(result.softWarnings()), first.meta());
        }

        log.info("[GoalDraft] HARD 위반 {}건 → AI 수정 요청 draftId={} shape={} rules={}",
                result.hardViolations().size(), job.draftId(), shape(plan),
                result.hardViolations().stream().map(PlanViolation::ruleId).distinct().toList());

        PlanGenerationResult revised = aiClient.generatePlan(request.forRevision(plan, result.hardViolations()));
        GoalPlanDto revisedPlan = pinUserFacts(normalize(revised.plan(), request.planningStyle(), today), request.slots(), today);
        PlanValidationResult revisedResult = validator.validate(revisedPlan, job.validationContext());
        AiCallMeta totalMeta = sumMeta(first.meta(), revised.meta());

        if (revisedResult.isValid()) {
            return new Outcome(revisedPlan.withWarnings(revisedResult.softWarnings()), totalMeta);
        }

        // 수정 후에도 위반이 남음 → 실패시키지 않고 더 나은 쪽을 경고와 함께 넘긴다
        boolean revisedBetter = revisedResult.hardViolations().size() <= result.hardViolations().size();
        GoalPlanDto best = revisedBetter ? revisedPlan : plan;
        PlanValidationResult bestResult = revisedBetter ? revisedResult : result;
        log.warn("[GoalDraft] 수정 후에도 위반 남음 → 경고로 넘김 draftId={} shape={} violations={}",
                job.draftId(), shape(best), bestResult.hardViolations());
        if (!usable(best)) {
            return new Outcome(null, totalMeta);
        }
        List<PlanViolation> warnings = new ArrayList<>(bestResult.hardViolations());
        warnings.addAll(bestResult.softWarnings());
        return new Outcome(best.withWarnings(warnings), totalMeta);
    }

    /** 루틴 시작 시각을 실제 하루에 맞춘다 (점심 등 고정 시간 · 다른 루틴 · 쉬는 시간 20분) */
    GoalPlanDto fitTimes(GoalPlanDto plan, GenerationJob job) {
        if (routineTimeFitter == null || job.userId() == null) return plan;
        var pref = job.validationContext().preference();
        return routineTimeFitter.fit(job.userId(), plan, job.validationContext().today(),
                pref != null ? pref.preferredTimeRanges() : null);
    }

    /**
     * "나에게 맞춘 점" (V47) — 만들어진 계획을 직접 확인해서 붙인다 (AI 가 쓴 말이 아니라 서버가 계산).
     * 계산이 실패해도 초안은 그대로 넘긴다.
     */
    static GoalPlanDto withFit(GoalPlanDto plan, GenerationJob job) {
        PersonalPlanContext personal = job.aiRequest().personal();
        if (personal == null) return plan;
        try {
            return plan.withFit(PlanFitBuilder.build(plan, personal));
        } catch (RuntimeException e) {
            log.warn("[GoalDraft] 나에게 맞춘 점 계산 실패 draftId={}", job.draftId(), e);
            return plan;
        }
    }

    /** 초안 화면에 띄워 사용자가 고칠 수 있는 계획인지 (목표 기간과 할 일이 있어야 화면이 성립) */
    static boolean usable(GoalPlanDto plan) {
        return plan != null && plan.goal() != null
                && plan.goal().startDate() != null && plan.goal().endDate() != null
                && plan.goal().endDate().isAfter(plan.goal().startDate())
                && plan.tasks() != null && !plan.tasks().isEmpty();
    }

    /**
     * 사용자가 직접 말한 값은 AI 가 "안전하게" 바꾸지 못하게 서버에서 고정한다.
     *  - 시작일 ("10월 29일부터"): goal.startDate 를 그 날로. AI 가 오늘부터 짰으면 루틴 · Task 날짜를 그만큼 뒤로 민다
     *  - 종료일 (사용자가 정한 기한, isFlexible=false): goal.endDate 를 그 날로
     *  - 목표 수치 ("60kg"): goal.metric.targetValue / startValue 를 사용자 값으로. 조정 의견은 aiNote 에만 남는다
     */
    static GoalPlanDto pinUserFacts(GoalPlanDto plan, GoalSlots slots, LocalDate today) {
        if (plan == null || plan.goal() == null || slots == null) return plan;
        PlanGoal g = plan.goal();
        LocalDate start = g.startDate();
        LocalDate end = g.endDate();

        DeadlineSlot d = slots.deadline().isFilled() ? slots.deadline().value() : null;
        if (d != null && d.endDate() != null && !d.isFlexible() && d.endDate().isAfter(today)) end = d.endDate();
        // AI 가 짠 기간 [aiStart, aiEnd] → 사용자가 정한 기간 [start, end] 로 날짜를 비율대로 옮긴다
        // (그냥 밀기만 하면 종료일이 고정일 때 뒤쪽 일정이 전부 종료일에 몰린다)
        LocalDate aiStart = g.startDate();
        LocalDate aiEnd = g.endDate();
        if (d != null && d.startDate() != null && !d.startDate().isBefore(today)
                && (end == null || d.startDate().isBefore(end))) {
            start = d.startDate();
        }
        PlanGoalMetric metric = g.metric();
        MetricSlot said = slots.metric().isFilled() ? slots.metric().value() : null;
        if (said != null && said.targetValue() != null) {
            if (metric != null) {
                metric = new PlanGoalMetric(metric.name(), metric.unit(),
                        said.currentValue() != null ? said.currentValue() : metric.startValue(), said.targetValue());
            } else if (said.currentValue() != null && said.name() != null && said.unit() != null) {
                metric = new PlanGoalMetric(said.name(), said.unit(), said.currentValue(), said.targetValue());
            }
        }

        PlanGoal fixed = new PlanGoal(g.title(), g.emoji(), g.category(), g.description(), metric,
                start, end, g.planningStyle(), g.aiNote(), g.subType());
        final DateFrame frame = new DateFrame(aiStart, aiEnd, start, end);
        List<PlanTask> tasks = plan.tasks() == null ? null : plan.tasks().stream()
                .map(t -> new PlanTask(t.tempId(), t.type(), t.subGoalTempId(), t.milestoneTempId(),
                        t.title(), t.emoji(), t.durationMinutes(), t.routineDays(), t.preferredStartTime(),
                        frame.map(t.startDate()), frame.map(t.endDate()),
                        frame.map(t.scheduledDate()), frame.map(t.dueDate()),
                        t.description(), t.reason()))
                .toList();
        return new GoalPlanDto(fixed, plan.subGoals(), plan.milestones(), tasks, plan.warnings());
    }

    /**
     * AI 기간 [fromStart, fromEnd] 의 날짜를 사용자 기간 [toStart, toEnd] 의 같은 비율 위치로.
     * 기간이 같으면 그대로, 정보가 없으면 [toStart, toEnd] 안으로 자르기만 한다. null 은 그대로.
     */
    record DateFrame(LocalDate fromStart, LocalDate fromEnd, LocalDate toStart, LocalDate toEnd) {
        LocalDate map(LocalDate date) {
            if (date == null) return null;
            LocalDate v = date;
            if (fromStart != null && fromEnd != null && toStart != null && toEnd != null
                    && fromEnd.isAfter(fromStart)
                    && (!fromStart.equals(toStart) || !fromEnd.equals(toEnd))) {
                long fromLen = java.time.temporal.ChronoUnit.DAYS.between(fromStart, fromEnd);
                long toLen = java.time.temporal.ChronoUnit.DAYS.between(toStart, toEnd);
                long off = java.time.temporal.ChronoUnit.DAYS.between(fromStart, date);
                v = toStart.plusDays(Math.round((double) off * toLen / fromLen));
            }
            if (toStart != null && v.isBefore(toStart)) v = toStart;
            if (toEnd != null && v.isAfter(toEnd)) v = toEnd;
            return v;
        }
    }

    /** AI가 성향을 다르게 써 넣거나 빠뜨려도 서버가 정한 성향으로 고정, AI가 넣은 warnings는 버림(서버가 채움) */
    private GoalPlanDto normalize(GoalPlanDto plan, PlanningStyle style, LocalDate today) {
        if (plan == null || plan.goal() == null) {
            throw new GoalAiException(GoalAiException.Reason.AI_INVALID_OUTPUT, "계획 또는 goal이 비어 있음");
        }
        PlanGoal g = plan.goal();
        PlanGoal fixed = new PlanGoal(g.title(), g.emoji(), g.category(), g.description(), g.metric(),
                g.startDate(), g.endDate(), style, g.aiNote(), g.subType());
        return GoalPlanRepairer.repair(simplify(
                new GoalPlanDto(fixed, plan.subGoals(), plan.milestones(), dropPrepTasks(plan.tasks()), null)), today);
    }

    /**
     * V52: 목표형은 "그래서 오늘 뭘 해야 해?"에 집중한다 — 마일스톤 · 여러 세부 목표는 화면에서 잘 안 보게 돼서 뺐다.
     * AI 가 프롬프트를 어기고 만들어도 여기서 정리한다.
     *  - 마일스톤은 모두 지우고 일회성 Task 의 마일스톤 연결도 끊는다 (일회성 Task 는 자기 마감일만 가진다)
     *  - 세부 목표는 첫 번째 하나만 남기고 모든 Task 를 거기로 모은다 (루틴 · Task 의 DB 그릇으로만 쓰고 화면에는 안 보임)
     * 기간에 따라 하는 일이 바뀌는 계획("2주 이론 → 기출")은 루틴의 startDate · endDate 로 나눠 표현한다.
     */
    static GoalPlanDto simplify(GoalPlanDto plan) {
        if (plan == null) return null;
        List<PlanSubGoal> subGoals = plan.subGoals() == null ? List.of() : plan.subGoals();
        String keep = subGoals.isEmpty() ? null : subGoals.get(0).tempId();
        List<PlanTask> tasks = plan.tasks() == null ? null : plan.tasks().stream()
                .map(t -> new PlanTask(t.tempId(), t.type(), keep != null ? keep : t.subGoalTempId(), null,
                        t.title(), t.emoji(), t.durationMinutes(), t.routineDays(), t.preferredStartTime(),
                        t.startDate(), t.endDate(), t.scheduledDate(), t.dueDate(), t.description(), t.reason()))
                .toList();
        return new GoalPlanDto(plan.goal(), subGoals.isEmpty() ? subGoals : List.of(subGoals.get(0)),
                List.of(), tasks, plan.warnings());
    }

    /**
     * AI 가 프롬프트를 어기고 만든 "운동화 끈 조정하기", "식재료 구매하기" 같은 준비용 일회성 Task 는 조용히 뺀다.
     * 일회성 Task 는 0개여도 되는 규칙이라 빼도 계획이 망가지지 않고, AI 를 한 번 더 부르는 것(시간·비용)보다 낫다.
     * 루틴은 빼면 계획 뼈대가 무너질 수 있어 검증(V5-6)에서 AI 에 다시 고쳐 달라고 한다.
     */
    static List<PlanTask> dropPrepTasks(List<PlanTask> tasks) {
        if (tasks == null) return null;
        List<PlanTask> kept = tasks.stream()
                .filter(t -> !(t.isOneTime() && t.title() != null && GoalPlanValidator.PREP_TASK.matcher(t.title()).find()))
                .toList();
        if (kept.size() != tasks.size()) {
            log.info("[GoalDraft] 준비용 일회성 Task {}개 제거", tasks.size() - kept.size());
        }
        return kept;
    }

    /** 실패 원인 파악용: 세부목표/마일스톤/루틴/일회성 개수 (예: "sg=4 ms=5 routine=0 oneTime=12") */
    static String shape(GoalPlanDto plan) {
        if (plan == null) return "null";
        List<PlanTask> tasks = plan.tasks() == null ? List.of() : plan.tasks();
        return "sg=" + (plan.subGoals() == null ? 0 : plan.subGoals().size())
                + " ms=" + (plan.milestones() == null ? 0 : plan.milestones().size())
                + " routine=" + tasks.stream().filter(PlanTask::isRoutine).count()
                + " oneTime=" + tasks.stream().filter(PlanTask::isOneTime).count();
    }

    /** 수정 요청까지 2번 호출했으면 토큰을 합쳐서 기록 (비용 추적용) */
    private AiCallMeta sumMeta(AiCallMeta a, AiCallMeta b) {
        if (a == null) return b;
        if (b == null) return a;
        return new AiCallMeta(b.model(), sum(a.inputTokens(), b.inputTokens()),
                sum(a.outputTokens(), b.outputTokens()), a.latencyMs() + b.latencyMs());
    }

    private Integer sum(Integer a, Integer b) {
        return a == null ? b : b == null ? a : a + b;
    }

    private long elapsed(long started) {
        return System.currentTimeMillis() - started;
    }
}
