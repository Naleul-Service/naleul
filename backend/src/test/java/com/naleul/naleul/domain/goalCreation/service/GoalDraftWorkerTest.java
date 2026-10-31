package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.goalCreation.client.*;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanTask;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.PlanViolation;
import com.naleul.naleul.domain.goalCreation.dto.plan.TaskType;
import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.goalCreation.dto.slot.GoalSlots;
import com.naleul.naleul.domain.goalCreation.dto.slot.Slot;
import com.naleul.naleul.domain.goalCreation.service.GoalDraftTxService.GenerationJob;
import com.naleul.naleul.domain.goalCreation.validator.GoalPlanValidator;
import com.naleul.naleul.domain.goalCreation.validator.PlanValidationContext;
import com.naleul.naleul.domain.goalCreation.validator.PlanValidationResult;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * GoalDraftWorker: AI 호출 → 검증 → (수정 요청) → 완료/실패 흐름 검증.
 * 실제 계획 내용은 FakeGoalAiClient로 만들고, 검증 결과는 Mock으로 조작한다.
 */
@ExtendWith(MockitoExtension.class)
class GoalDraftWorkerTest {

    @Mock private GoalAiClient aiClient;
    @Mock private GoalPlanValidator validator;
    @Mock private GoalDraftTxService txService;

    private GoalDraftWorker worker;

    private static final Long DRAFT_ID = 555L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    private GenerationJob job;
    private PlanGenerationResult fakeResult;

    private static final PlanValidationResult VALID = new PlanValidationResult(List.of(), List.of());
    private static final PlanViolation HARD = new PlanViolation("V3-2", "milestones[3].dueDate", "마지막 마일스톤...");
    private static final PlanValidationResult INVALID = new PlanValidationResult(List.of(HARD), List.of());

    @BeforeEach
    void setUp() {
        worker = new GoalDraftWorker(aiClient, validator, txService);

        GoalSlots slots = GoalSlots.empty().withGoalStatement(Slot.filled("몸무게 10kg 감량"));
        PlanGenerationRequest request = new PlanGenerationRequest(
                slots, PlanningStyle.SPONTANEOUS, TODAY, 300, null, null, List.of(), null);
        job = new GenerationJob(DRAFT_ID, 100L, 1, request,
                new PlanValidationContext(TODAY, PlanningStyle.SPONTANEOUS, null, 300));
        fakeResult = new FakeGoalAiClient().generatePlan(request);
    }

    @Test
    void 첫_결과가_통과하면_AI를_한번만_부르고_완료처리한다() {
        given(aiClient.generatePlan(any())).willReturn(fakeResult);
        given(validator.validate(any(), any())).willReturn(VALID);

        worker.generate(job);

        then(aiClient).should(times(1)).generatePlan(any());
        then(txService).should().completeDraft(eq(DRAFT_ID), any(), any());
        then(txService).should(never()).failDraft(any(), any());
    }

    @Test
    void HARD_위반이면_위반목록을_붙여_한번_더_요청하고_통과하면_완료한다() {
        given(aiClient.generatePlan(any())).willReturn(fakeResult);
        given(validator.validate(any(), any())).willReturn(INVALID).willReturn(VALID);

        worker.generate(job);

        ArgumentCaptor<PlanGenerationRequest> captor = ArgumentCaptor.forClass(PlanGenerationRequest.class);
        then(aiClient).should(times(2)).generatePlan(captor.capture());
        PlanGenerationRequest revision = captor.getAllValues().get(1);
        assertThat(revision.violationsToFix()).containsExactly(HARD);
        assertThat(revision.previousPlan()).isNotNull();
        then(txService).should().completeDraft(eq(DRAFT_ID), any(), any());
    }

    @Test
    void 수정_후에도_HARD_위반이_남아도_실패시키지_않고_경고로_붙여_완료한다() {
        given(aiClient.generatePlan(any())).willReturn(fakeResult);
        given(validator.validate(any(), any())).willReturn(INVALID);

        worker.generate(job);

        then(aiClient).should(times(2)).generatePlan(any());   // 최대 2회
        then(txService).should(never()).failDraft(any(), any());
        ArgumentCaptor<GoalPlanDto> saved = ArgumentCaptor.forClass(GoalPlanDto.class);
        then(txService).should().completeDraft(eq(DRAFT_ID), saved.capture(), any());
        assertThat(saved.getValue().warnings()).extracting(PlanViolation::ruleId).contains("V3-2");
    }

    @Test
    void 할_일이_하나도_없는_계획은_쓸_수_없어_실패한다() {
        GoalPlanDto empty = new GoalPlanDto(fakeResult.plan().goal(), fakeResult.plan().subGoals(),
                fakeResult.plan().milestones(), List.of(), null);
        given(aiClient.generatePlan(any())).willReturn(new PlanGenerationResult(empty, fakeResult.meta()));
        given(validator.validate(any(), any())).willReturn(INVALID);

        worker.generate(job);

        then(txService).should().failDraft(DRAFT_ID, "AI_PLAN_INVALID");
        then(txService).should(never()).completeDraft(any(), any(), any());
    }

    @Test
    void AI_타임아웃이면_AI_TIMEOUT으로_실패한다() {
        given(aiClient.generatePlan(any()))
                .willThrow(new GoalAiException(GoalAiException.Reason.AI_TIMEOUT, "timeout"));

        worker.generate(job);

        then(txService).should().failDraft(DRAFT_ID, "AI_TIMEOUT");
    }

    @Test
    void 예상못한_예외도_삼키지_않고_실패처리한다() {
        given(aiClient.generatePlan(any())).willThrow(new IllegalStateException("boom"));

        worker.generate(job);

        then(txService).should().failDraft(DRAFT_ID, "AI_UNAVAILABLE");
    }

    @Test
    void 완료된_계획은_서버가_정한_성향과_SOFT경고를_담는다() {
        PlanViolation soft = new PlanViolation("V5-4", "tasks[1].durationMinutes", "제목과 시간이 달라요");
        given(aiClient.generatePlan(any())).willReturn(fakeResult);
        given(validator.validate(any(), any())).willReturn(new PlanValidationResult(List.of(), List.of(soft)));

        worker.generate(job);

        ArgumentCaptor<GoalPlanDto> captor = ArgumentCaptor.forClass(GoalPlanDto.class);
        then(txService).should().completeDraft(eq(DRAFT_ID), captor.capture(), any());
        assertThat(captor.getValue().goal().planningStyle()).isEqualTo(PlanningStyle.SPONTANEOUS);
        assertThat(captor.getValue().warnings()).containsExactly(soft);
    }

    @Test
    void 준비용_일회성_Task는_조용히_빼고_루틴은_남긴다() {
        List<PlanTask> tasks = List.of(
                new PlanTask("t1", TaskType.ROUTINE, "sg1", null, "하체 근력 50분", null, 50,
                        List.of(Weekday.MON), null, null, null, null, null, "스쿼트 4x10"),
                new PlanTask("t2", TaskType.ONE_TIME, "sg1", "ms1", "저칼로리 식재료 구매하기", null, 20,
                        null, null, null, null, null, null),
                new PlanTask("t3", TaskType.ONE_TIME, "sg1", "ms1", "5km 기록 측정", null, 40,
                        null, null, null, null, null, null));

        assertThat(GoalDraftWorker.dropPrepTasks(tasks)).extracting(PlanTask::tempId).containsExactly("t1", "t3");
    }
}
