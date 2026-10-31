package com.naleul.naleul.domain.goalCreation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.validator.GoalCategoryValidator;
import com.naleul.naleul.domain.goalCreation.client.FakeGoalAiClient;
import com.naleul.naleul.domain.goalCreation.client.PlanGenerationRequest;
import com.naleul.naleul.domain.goalCreation.config.GoalAiProperties;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanGoal;
import com.naleul.naleul.domain.goalCreation.dto.request.GoalConfirmRequest;
import com.naleul.naleul.domain.goalCreation.dto.response.GoalConfirmResponse;
import com.naleul.naleul.domain.goalCreation.dto.slot.*;
import com.naleul.naleul.domain.goalCreation.entity.GoalCreationSession;
import com.naleul.naleul.domain.goalCreation.entity.GoalDraft;
import com.naleul.naleul.domain.goalCreation.enums.GoalCreationSessionStatus;
import com.naleul.naleul.domain.goalCreation.event.GoalCreatedEvent;
import com.naleul.naleul.domain.goalCreation.exception.GoalPlanValidationException;
import com.naleul.naleul.domain.goalCreation.repository.GoalCreationSessionRepository;
import com.naleul.naleul.domain.goalCreation.repository.GoalDraftRepository;
import com.naleul.naleul.domain.goalCreation.support.GoalClock;
import com.naleul.naleul.domain.goalCreation.support.GoalJsonConverter;
import com.naleul.naleul.domain.goalCreation.validator.GoalPlanValidator;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import com.naleul.naleul.domain.user.enums.PlanningStyleSource;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

/**
 * GoalConfirmService: 확정 전 확인(상태/최신 초안/검증/한도)과 저장 이후 처리(성향, 세션, 이벤트) 검증.
 * 실제 저장은 GoalPlanPersister(Mock)가 하므로 GoalPlanPersisterTest에서 따로 본다.
 * 검증기(GoalPlanValidator)는 순수 로직이라 실제 객체를 쓴다.
 */
@ExtendWith(MockitoExtension.class)
class GoalConfirmServiceTest {

    @Mock private GoalCreationSessionRepository sessionRepository;
    @Mock private GoalDraftRepository draftRepository;
    @Mock private GoalCategoryValidator goalCategoryValidator;
    @Mock private GoalColorAssigner colorAssigner;
    @Mock private GoalPlanPersister planPersister;
    @Mock private GoalClock clock;
    @Mock private ApplicationEventPublisher eventPublisher;

    private GoalConfirmService confirmService;
    private GoalJsonConverter jsonConverter;

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 100L;
    private static final Long DRAFT_ID = 555L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);
    private static final LocalDateTime NOW = TODAY.atTime(10, 0);

    private User user;
    private GoalCreationSession session;
    private GoalDraft draft;
    private GoalPlanDto validPlan;

    @BeforeEach
    void setUp() {
        jsonConverter = new GoalJsonConverter(new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        GoalAiProperties properties = new GoalAiProperties("fake", null, null, 20, 80, 5, 300, 5, 5, 10, 7, 3, 10, 20, java.util.List.of(), 3);
        confirmService = new GoalConfirmService(sessionRepository, draftRepository, new GoalPlanValidator(),
                goalCategoryValidator, colorAssigner, planPersister, jsonConverter, properties, clock, eventPublisher);

        user = User.builder().userId(USER_ID).build();
        GoalSlots slots = GoalSlots.empty()
                .withGoalStatement(Slot.filled("몸무게 10kg 감량"))
                .withMetric(Slot.filled(new MetricSlot("체중", BigDecimal.valueOf(78), BigDecimal.valueOf(68), "kg")))
                .withMotivation(Slot.filled("지방간 개선"));

        session = GoalCreationSession.start(user, null, jsonConverter.toJson(slots), NOW.plusDays(7));
        session.finishInterview();
        session.startGenerating();
        session.markDraftReady();   // DRAFT_READY

        validPlan = new FakeGoalAiClient().generatePlan(new PlanGenerationRequest(
                slots, PlanningStyle.PLANNER, TODAY, 300, null, null, List.of(), null)).plan();

        draft = GoalDraft.generating(session, 1, PlanningStyle.PLANNER, null);
        draft.markReady(jsonConverter.toJson(validPlan), "fake", 0, 0);
        ReflectionTestUtils.setField(draft, "goalDraftId", DRAFT_ID);
    }

    private void givenConfirmable() {
        given(sessionRepository.findOwnedForUpdate(SESSION_ID, USER_ID)).willReturn(Optional.of(session));
        given(clock.now()).willReturn(NOW);
        given(draftRepository.findTopBySession_GoalCreationSessionIdOrderByVersionDesc(SESSION_ID))
                .willReturn(Optional.of(draft));
    }

    private GoalConfirmRequest request(GoalPlanDto plan, boolean selectedByUser) {
        return GoalConfirmRequest.builder().draftId(DRAFT_ID).plan(plan).planningStyleSelectedByUser(selectedByUser).build();
    }

    @Test
    void 정상_확정이면_저장하고_세션을_CONFIRMED로_바꾸고_이벤트를_발행한다() {
        givenConfirmable();
        given(clock.today()).willReturn(TODAY);
        GoalCategory saved = GoalCategory.create(user, null, "몸무게 10kg 감량", TODAY, TODAY.plusMonths(6), null);
        ReflectionTestUtils.setField(saved, "goalCategoryId", 12L);
        given(planPersister.persist(eq(user), any(), any(), eq(PlanningStyle.PLANNER), eq("지방간 개선"), anyBoolean(), any()))
                .willReturn(saved);

        GoalConfirmResponse response = confirmService.confirm(USER_ID, SESSION_ID, request(validPlan, false));

        assertThat(response.goalId()).isEqualTo(12L);
        assertThat(session.getStatus()).isEqualTo(GoalCreationSessionStatus.CONFIRMED);
        assertThat(session.getConfirmedGoalCategory()).isEqualTo(saved);
        assertThat(user.getPlanningStyle()).isEqualTo(PlanningStyle.PLANNER);
        assertThat(user.getPlanningStyleSource()).isEqualTo(PlanningStyleSource.AI_INFERRED);
        then(goalCategoryValidator).should().validateCreate(eq(user), any());
        then(eventPublisher).should().publishEvent(new GoalCreatedEvent(12L, USER_ID));
    }

    @Test
    void 예전_목표에서_시작했으면_새_목표를_만들지_않고_그_목표에_계획을_채운다() {
        GoalCategory legacy = GoalCategory.create(user, null, "다이어트", TODAY.minusMonths(3), null, null);
        ReflectionTestUtils.setField(legacy, "goalCategoryId", 7L);
        ReflectionTestUtils.setField(session, "sourceGoalCategory", legacy);
        givenConfirmable();
        given(clock.today()).willReturn(TODAY);
        given(planPersister.persistInto(eq(legacy), eq(user), any(), eq(PlanningStyle.PLANNER), eq("지방간 개선"),
                anyBoolean(), any())).willReturn(legacy);

        GoalConfirmResponse response = confirmService.confirm(USER_ID, SESSION_ID, request(validPlan, false));

        assertThat(response.goalId()).isEqualTo(7L);
        assertThat(session.getConfirmedGoalCategory()).isEqualTo(legacy);
        then(goalCategoryValidator).should(never()).validateCreate(any(), any());   // 목표 개수 한도는 보지 않음
        then(colorAssigner).should(never()).assign(any(), any());                   // 색은 그대로
        then(planPersister).should(never()).persist(any(), any(), any(), any(), any(), anyBoolean(), any());
        then(eventPublisher).should().publishEvent(new GoalCreatedEvent(7L, USER_ID));
    }

    @Test
    void 확정_전에_예전_목표가_업무형으로_바뀌었으면_400() {
        GoalCategory legacy = GoalCategory.create(user, null, "회사 일", TODAY.minusMonths(3), null, null);
        legacy.keepAsRecord();
        ReflectionTestUtils.setField(session, "sourceGoalCategory", legacy);
        givenConfirmable();
        given(clock.today()).willReturn(TODAY);

        assertThatThrownBy(() -> confirmService.confirm(USER_ID, SESSION_ID, request(validPlan, false)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_SOURCE_GOAL);
        then(planPersister).should(never()).persistInto(any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void 사용자가_성향을_직접_골랐으면_USER_SET으로_저장한다() {
        givenConfirmable();
        given(clock.today()).willReturn(TODAY);
        PlanGoal g = validPlan.goal();
        GoalPlanDto spontaneous = new GoalPlanDto(new PlanGoal(g.title(), g.emoji(), g.category(), g.description(),
                g.metric(), g.startDate(), g.endDate(), PlanningStyle.SPONTANEOUS, g.aiNote()),
                validPlan.subGoals(), validPlan.milestones(), validPlan.tasks(), null);
        given(planPersister.persist(any(), any(), any(), any(), any(), anyBoolean(), any()))
                .willReturn(GoalCategory.create(user, null, "x", TODAY, TODAY.plusMonths(6), null));

        confirmService.confirm(USER_ID, SESSION_ID, request(spontaneous, true));

        assertThat(user.getPlanningStyle()).isEqualTo(PlanningStyle.SPONTANEOUS);
        assertThat(user.getPlanningStyleSource()).isEqualTo(PlanningStyleSource.USER_SET);
    }

    @Test
    void 화면의_초안이_최신이_아니면_409() {
        givenConfirmable();
        GoalConfirmRequest stale = GoalConfirmRequest.builder().draftId(DRAFT_ID - 1).plan(validPlan).build();

        assertThatThrownBy(() -> confirmService.confirm(USER_ID, SESSION_ID, stale))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GOAL_SESSION_INVALID_STATE);
        then(planPersister).should(never()).persist(any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void 이미_확정된_세션에_다시_확정하면_409() {
        session.confirm(GoalCategory.create(user, null, "x", TODAY, TODAY.plusMonths(1), null));
        given(sessionRepository.findOwnedForUpdate(SESSION_ID, USER_ID)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> confirmService.confirm(USER_ID, SESSION_ID, request(validPlan, false)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GOAL_SESSION_INVALID_STATE);
    }

    @Test
    void 사용자가_고친_초안이_HARD_위반이면_422와_위반목록() {
        givenConfirmable();
        given(clock.today()).willReturn(TODAY);
        PlanGoal g = validPlan.goal();
        GoalPlanDto broken = new GoalPlanDto(new PlanGoal("", g.emoji(), g.category(), g.description(),
                g.metric(), g.startDate(), g.endDate(), g.planningStyle(), g.aiNote()),
                validPlan.subGoals(), validPlan.milestones(), validPlan.tasks(), null);

        assertThatThrownBy(() -> confirmService.confirm(USER_ID, SESSION_ID, request(broken, false)))
                .isInstanceOfSatisfying(GoalPlanValidationException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DRAFT_VALIDATION_FAILED);
                    assertThat(e.getViolations()).extracting("path").contains("goal.title");
                });
        then(planPersister).should(never()).persist(any(), any(), any(), any(), any(), anyBoolean(), any());
        assertThat(session.getStatus()).isEqualTo(GoalCreationSessionStatus.DRAFT_READY);
    }

    @Test
    void 무료플랜_목표개수_한도를_넘으면_저장하지_않는다() {
        givenConfirmable();
        given(clock.today()).willReturn(TODAY);
        willThrow(new CustomException(ErrorCode.GOAL_CATEGORY_LIMIT_EXCEEDED))
                .given(goalCategoryValidator).validateCreate(eq(user), any());

        assertThatThrownBy(() -> confirmService.confirm(USER_ID, SESSION_ID, request(validPlan, false)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GOAL_CATEGORY_LIMIT_EXCEEDED);
        then(planPersister).should(never()).persist(any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void 자정을_넘겨_지나간_시작일과_실행일은_오늘로_당긴다() {
        LocalDate tomorrow = TODAY.plusDays(1);

        GoalPlanDto shifted = confirmService.shiftPastDatesToToday(validPlan, tomorrow);

        assertThat(shifted.goal().startDate()).isEqualTo(tomorrow);
        assertThat(shifted.tasks()).filteredOn(t -> t.isRoutine())
                .allSatisfy(t -> assertThat(t.startDate()).isEqualTo(tomorrow));
        assertThat(shifted.goal().endDate()).isEqualTo(validPlan.goal().endDate());   // 종료일은 그대로
    }
}
