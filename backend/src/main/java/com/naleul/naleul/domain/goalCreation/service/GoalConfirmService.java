package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.goalCategory.dto.request.GoalCategoryCreateRequest;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.kind.GoalKind;
import com.naleul.naleul.domain.goalCategory.validator.GoalCategoryValidator;
import com.naleul.naleul.domain.goalCreation.config.GoalAiProperties;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanGoal;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanTask;
import com.naleul.naleul.domain.goalCreation.dto.request.GoalConfirmRequest;
import com.naleul.naleul.domain.goalCreation.dto.response.GoalConfirmResponse;
import com.naleul.naleul.domain.goalCreation.dto.slot.GoalSlots;
import com.naleul.naleul.domain.goalCreation.entity.GoalCreationSession;
import com.naleul.naleul.domain.goalCreation.entity.GoalDraft;
import com.naleul.naleul.domain.goalCreation.entity.GoalPlanFitRecord;
import com.naleul.naleul.domain.goalCreation.personal.CapacityProfileReader;
import com.naleul.naleul.domain.goalCreation.personal.PlanEditCounter;
import com.naleul.naleul.domain.goalCreation.personal.PlanFitBuilder;
import com.naleul.naleul.domain.goalCreation.repository.GoalPlanFitRecordRepository;
import com.naleul.naleul.domain.goalCreation.enums.GoalCreationSessionStatus;
import com.naleul.naleul.domain.goalCreation.event.DraftConfirmedEvent;
import com.naleul.naleul.domain.goalCreation.event.GoalCreatedEvent;
import com.naleul.naleul.domain.goalCreation.exception.GoalPlanValidationException;
import com.naleul.naleul.domain.goalCreation.repository.GoalCreationSessionRepository;
import com.naleul.naleul.domain.goalCreation.repository.GoalDraftRepository;
import com.naleul.naleul.domain.goalCreation.support.GoalClock;
import com.naleul.naleul.domain.goalCreation.support.GoalJsonConverter;
import com.naleul.naleul.domain.goalCreation.validator.GoalPlanValidator;
import com.naleul.naleul.domain.goalCreation.validator.PlanValidationContext;
import com.naleul.naleul.domain.goalCreation.validator.PlanValidationResult;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import com.naleul.naleul.domain.user.enums.PlanningStyleSource;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 초안 확정 (명세 5-2). AI 호출이 없으므로 처음부터 끝까지 하나의 트랜잭션.
 * 중간에 하나라도 실패하면 목표/세부 목표/마일스톤/루틴/Task 전부 롤백된다.
 *
 * 순서
 *  1. 세션 잠금 조회(중복 확정 방지) + 상태 확인
 *  2. 요청한 draftId가 이 세션의 "최신 READY 초안"인지 확인
 *  3. 자정을 넘겨 지나간 날짜는 오늘로 당김 → 서버 검증(HARD면 422 + violations)
 *  4. 무료 플랜 목표 개수 한도 (A안: 목표 개수만)
 *  5. 색상 결정 → 저장 → 사용자 성향 저장 → 세션 CONFIRMED
 *  6. GoalCreatedEvent 발행 (리스너는 커밋 후 실행되도록 AFTER_COMMIT으로 받아야 함)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GoalConfirmService {

    private final GoalCreationSessionRepository sessionRepository;
    private final GoalDraftRepository draftRepository;
    private final GoalPlanValidator planValidator;
    private final GoalCategoryValidator goalCategoryValidator;
    private final GoalColorAssigner colorAssigner;
    private final GoalPlanPersister planPersister;
    private final GoalJsonConverter jsonConverter;
    private final GoalAiProperties properties;
    private final GoalClock clock;
    private final ApplicationEventPublisher eventPublisher;

    /** 개인 맞춤 효과 측정 (V47) — 기존 테스트 생성자 호환을 위해 필드 주입 */
    @Autowired(required = false)
    private GoalPlanFitRecordRepository planFitRepository;
    @Autowired(required = false)
    private CapacityProfileReader capacityReader;

    public GoalConfirmResponse confirm(Long userId, Long sessionId, GoalConfirmRequest request) {
        // 1) 잠금 조회: 두 번 연타해도 두 번째 요청은 첫 번째 커밋 후 CONFIRMED를 보고 409
        GoalCreationSession session = sessionRepository.findOwnedForUpdate(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.GOAL_SESSION_NOT_FOUND));
        if (session.getStatus() != GoalCreationSessionStatus.DRAFT_READY || session.isExpired(clock.now())) {
            throw new CustomException(ErrorCode.GOAL_SESSION_INVALID_STATE);
        }

        // 2) 화면에 띄운 초안이 최신 READY 초안이어야 함 (다른 기기에서 다시 생성한 경우 등)
        GoalDraft latest = draftRepository.findTopBySession_GoalCreationSessionIdOrderByVersionDesc(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.GOAL_DRAFT_NOT_FOUND));
        if (!latest.getGoalDraftId().equals(request.getDraftId()) || !latest.isReady()) {
            throw new CustomException(ErrorCode.GOAL_SESSION_INVALID_STATE);
        }

        // 3) 검증
        LocalDate today = clock.today();
        GoalPlanDto draftPlan = jsonConverter.toPlan(latest.getPayload());
        GoalPlanDto plan = fillRoutineDescriptions(shiftPastDatesToToday(request.getPlan(), today), draftPlan);
        PlanningStyle style = resolveStyle(plan, latest);
        GoalSlots slots = jsonConverter.toSlots(session.getSlots());

        // 예전 목표의 기존 루틴은 이미 하고 있는 일이라 검증하지 않는다 (2026-10). 새로 더한 할 일이 없으면 검증할 것도 없다
        GoalPlanDto toValidate = session.getSourceGoalCategory() != null && !session.getSourceGoalCategory().isTemporary()
                ? LegacyPlanMerger.withoutExisting(plan) : plan;
        PlanValidationResult result = toValidate.tasks().isEmpty() && !plan.tasks().isEmpty()
                ? new PlanValidationResult(List.of(), List.of())
                : planValidator.validate(toValidate, new PlanValidationContext(
                today, style,
                slots.practicePreference().isFilled() ? slots.practicePreference().value() : null,
                properties.maxWeeklyRoutineMinutes()));
        if (!result.isValid()) {
            log.info("[GoalConfirm] 검증 실패 sessionId={} rules={}", sessionId,
                    result.hardViolations().stream().map(v -> v.ruleId()).distinct().toList());
            throw new GoalPlanValidationException(result.hardViolations());
        }

        // 4) 무료 플랜 목표 개수 한도 + 날짜 범위 (기존 목표 생성과 같은 검증기 재사용)
        //    예전 목표를 AI 로 다시 설계하는 경우(2026-10)는 새 목표가 생기지 않으므로 개수 한도를 보지 않는다
        User user = session.getUser();
        PlanGoal g = plan.goal();
        GoalCategory legacy = legacySource(session);
        if (legacy == null) {
            goalCategoryValidator.validateCreate(user, GoalCategoryCreateRequest.builder()
                    .goalCategoryName(g.title())
                    .goalCategoryStartDate(g.startDate())
                    .goalCategoryEndDate(g.endDate())
                    .build());
        }

        // 5) 저장
        String motivation = slots.motivation().isFilled() ? slots.motivation().value() : null;
        boolean deadlineFlexible = slots.deadline().isFilled() && slots.deadline().value().isFlexible();
        // 목표 종류: 사용자가 처음에 고른 값 → 없으면(예전 앱) AI 가 분류한 값
        GoalKind kind = session.kind() != null ? session.kind()
                : g.subType() != null ? GoalKind.restore(null, g.subType(), null) : null;
        // 예전 목표는 원래 색을 그대로 쓰고, 초안 화면에서 색을 직접 골랐을 때만 바꾼다
        if (legacy != null && request.getColorId() != null) {
            legacy.updateColor(colorAssigner.assign(user, request.getColorId()));
        }
        GoalCategory goal = legacy != null
                ? planPersister.persistInto(legacy, user, plan, style, motivation, deadlineFlexible, kind)
                : planPersister.persist(user, colorAssigner.assign(user, request.getColorId()),
                        plan, style, motivation, deadlineFlexible, kind);

        user.updatePlanningStyle(style, request.isPlanningStyleSelectedByUser()
                ? PlanningStyleSource.USER_SET
                : PlanningStyleSource.AI_INFERRED);
        session.confirm(goal);
        recordPlanFit(goal, user, draftPlan, plan);

        // 6) 이벤트 (리스너가 AFTER_COMMIT이면 이 트랜잭션이 커밋된 뒤에 실행됨)
        eventPublisher.publishEvent(new GoalCreatedEvent(goal.getGoalCategoryId(), userId));
        // V68: AI 초안 정확도 — 비교는 화면 그대로의 계획으로 (날짜를 오늘로 당기기 전)
        eventPublisher.publishEvent(new DraftConfirmedEvent(sessionId, goal.getGoalCategoryId(), userId,
                latest.getGoalDraftId(), fillRoutineDescriptions(request.getPlan(), draftPlan)));

        return new GoalConfirmResponse(goal.getGoalCategoryId());
    }

    /**
     * 예전 목표(AI 없이 직접 만든 목표)에서 시작한 세션이면 그 목표 (2026-10). 확정 시점에 다시 확인한다
     * — 세션을 연 뒤 다른 기기에서 완료 · 삭제 · 업무형 전환했으면 422 대신 400 INVALID_SOURCE_GOAL.
     * 임시 목표에서 시작했거나 출발 목표가 없으면 null (새 목표를 만든다)
     */
    private GoalCategory legacySource(GoalCreationSession session) {
        GoalCategory source = session.getSourceGoalCategory();
        if (source == null || source.isTemporary()) return null;
        if (!source.isLegacyManual()) throw new CustomException(ErrorCode.INVALID_SOURCE_GOAL);
        return source;
    }

    /**
     * 개인 맞춤 효과 측정 (V47 goal_plan_fit) — 개인 맞춤으로 만든 초안일 때만.
     * 측정용이라 실패해도 목표 확정은 그대로 진행한다.
     */
    private void recordPlanFit(GoalCategory goal, User user, GoalPlanDto draftPlan, GoalPlanDto confirmed) {
        if (planFitRepository == null || draftPlan == null || draftPlan.fit() == null) return;
        try {
            GoalPlanDto.PlanFit fit = draftPlan.fit();
            int confirmedWeekly = PlanFitBuilder.weeklyMinutes(confirmed.tasks().stream().filter(PlanTask::isRoutine).toList());
            Integer rate = capacityReader != null && fit.basedOnHistory()
                    ? capacityReader.read(user.getUserId()).executionRate() : null;
            planFitRepository.save(GoalPlanFitRecord.of(goal, user, fit.intensity(), fit.basedOnHistory(), rate,
                    fit.weeklyCapMinutes(), fit.planWeeklyMinutes(), confirmedWeekly,
                    draftPlan.tasks().size(), PlanEditCounter.count(draftPlan, confirmed)));
        } catch (RuntimeException e) {
            log.warn("[GoalConfirm] 개인 맞춤 기록 실패 goalCategoryId={}", goal.getGoalCategoryId(), e);
        }
    }

    /** 사용자가 직접 바꾼 성향이 plan에 들어오므로 plan 값을 우선, 없으면 초안 생성 때 쓴 성향 */
    private PlanningStyle resolveStyle(GoalPlanDto plan, GoalDraft draft) {
        PlanningStyle fromPlan = plan.goal() != null ? plan.goal().planningStyle() : null;
        return fromPlan != null ? fromPlan : draft.getPlanningStyle();
    }

    /**
     * 루틴 설명(description)이 확정 요청에서 빠졌으면 AI 초안의 같은 루틴(tempId) 설명으로 채운다.
     * 예전 iOS 앱은 description 필드를 몰라서 초안을 그대로 돌려보내도 이 값이 사라진다.
     * 사용자가 화면에서 지운 게 아니라 "모르는 필드라 빠진 것"이므로 원래 설명을 살린다.
     */
    GoalPlanDto fillRoutineDescriptions(GoalPlanDto plan, GoalPlanDto draftPlan) {
        if (plan == null || draftPlan == null) return plan;
        Map<String, String> byTempId = new HashMap<>();
        draftPlan.tasks().forEach(t -> {
            if (t.tempId() != null && t.description() != null) byTempId.put(t.tempId(), t.description());
        });
        if (byTempId.isEmpty()) return plan;
        List<PlanTask> tasks = plan.tasks().stream().map(t -> {
            if (t.description() != null || t.tempId() == null || !byTempId.containsKey(t.tempId())) return t;
            return new PlanTask(t.tempId(), t.type(), t.subGoalTempId(), t.milestoneTempId(), t.title(), t.emoji(),
                    t.durationMinutes(), t.routineDays(), t.preferredStartTime(), t.startDate(), t.endDate(),
                    t.scheduledDate(), t.dueDate(), byTempId.get(t.tempId()), t.reason());
        }).toList();
        return new GoalPlanDto(plan.goal(), plan.subGoals(), plan.milestones(), tasks, plan.warnings());
    }

    /**
     * 밤 11시 59분에 만든 초안을 자정 넘어 확정하면 시작일이 "어제"가 되어 V1-1에 걸린다.
     * 시작일·루틴 시작일·일회성 실행일이 오늘보다 이전이면 오늘로 당긴다.
     * (마감일은 건드리지 않음 — 이미 지난 마감은 사용자가 고쳐야 하는 진짜 오류이므로 422로 알려준다)
     */
    GoalPlanDto shiftPastDatesToToday(GoalPlanDto plan, LocalDate today) {
        if (plan == null || plan.goal() == null) return plan;
        PlanGoal g = plan.goal();
        PlanGoal goal = g.startDate() != null && g.startDate().isBefore(today)
                ? new PlanGoal(g.title(), g.emoji(), g.category(), g.description(), g.metric(),
                today, g.endDate(), g.planningStyle(), g.aiNote(), g.subType())
                : g;

        List<PlanTask> tasks = plan.tasks().stream().map(t -> {
            boolean routineStartPast = t.isRoutine() && t.startDate() != null && t.startDate().isBefore(today);
            boolean scheduledPast = t.isOneTime() && t.scheduledDate() != null && t.scheduledDate().isBefore(today);
            if (!routineStartPast && !scheduledPast) return t;
            return new PlanTask(t.tempId(), t.type(), t.subGoalTempId(), t.milestoneTempId(), t.title(), t.emoji(),
                    t.durationMinutes(), t.routineDays(), t.preferredStartTime(),
                    routineStartPast ? today : t.startDate(), t.endDate(),
                    scheduledPast ? today : t.scheduledDate(), t.dueDate(), t.description(), t.reason());
        }).toList();

        return new GoalPlanDto(goal, plan.subGoals(), plan.milestones(), tasks, plan.warnings());
    }
}
