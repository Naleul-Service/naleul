package com.naleul.naleul.domain.goalCreation.client.claude;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.naleul.naleul.domain.goalCreation.client.*;
import com.naleul.naleul.domain.goalCreation.config.ClaudeGoalAiProperties;
import com.naleul.naleul.domain.goalCreation.config.GoalAiProperties;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.slot.*;
import com.naleul.naleul.domain.goalCreation.enums.MessageRole;
import com.naleul.naleul.domain.goalCreation.validator.GoalPlanValidator;
import com.naleul.naleul.domain.goalCreation.validator.PlanValidationContext;
import com.naleul.naleul.domain.goalCreation.validator.PlanValidationResult;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Claude API를 호출하는 확인용 테스트 (비용 발생!).
 * ANTHROPIC_API_KEY와 AI_MODEL 환경변수가 있을 때만 실행되고, 평소 ./gradlew test에서는 건너뛴다.
 *
 * 실행 예)
 *   ANTHROPIC_API_KEY=sk-ant-... AI_MODEL=claude-haiku-4-5-20251001 \
 *   ./gradlew test --tests "*ClaudeGoalAiLiveTest"
 *
 * 콘솔에 출력되는 계획/위반 목록을 눈으로 보고 프롬프트(resources/ai/goal-creation/*.txt)를 다듬는 데 쓴다.
 */
@Tag("live-ai")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AI_MODEL", matches = ".+")
class ClaudeGoalAiLiveTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

    private ClaudeGoalAiClient client() {
        GoalAiProperties props = new GoalAiProperties("claude", null, null, 20, 80, 5, 300, 5, 5, 10, 7, 3, 10, 20, java.util.List.of(), 3);
        boolean force = Boolean.parseBoolean(System.getenv().getOrDefault("AI_FORCE_TOOL", "false"));
        ClaudeGoalAiProperties claudeProps = new ClaudeGoalAiProperties(force, 1024, 8192,
                "https://api.anthropic.com/v1/messages");
        return new ClaudeGoalAiClient(System.getenv("ANTHROPIC_API_KEY"), System.getenv("AI_MODEL"),
                props, claudeProps, objectMapper,
                com.naleul.naleul.domain.aiUsage.recorder.AiCallRecorder.noop());
    }

    @Test
    void 인터뷰_한턴() {
        InterviewTurnResult result = client().interviewTurn(new InterviewTurnRequest(
                GoalSlots.empty(),
                List.of(new ConversationMessage(MessageRole.USER, "살 10kg 빼고 싶어. 지금 78kg이야", null, false)),
                5, today, null));

        System.out.println("nextAction=" + result.nextAction() + " targetSlot=" + result.targetSlot());
        System.out.println("question=" + result.question() + " quickReplies=" + result.quickReplies());
        System.out.println("extracted=" + result.extracted());
        assertThat(result.nextAction()).isNotNull();
        assertThat(result.extracted().goalStatement()).isNotBlank();
    }

    @Test
    void 감량_예시_계획이_수정요청_1회_이내에_검증을_통과한다() throws Exception {
        GoalSlots slots = GoalSlots.empty()
                .withGoalStatement(Slot.filled("몸무게 10kg 감량"))
                .withMetric(Slot.filled(new MetricSlot("체중", BigDecimal.valueOf(78), BigDecimal.valueOf(68), "kg")))
                .withMotivation(Slot.filled("건강검진에서 지방간이 나왔고, 예전 옷을 다시 입고 싶어서"))
                .withDeadline(Slot.filled(new DeadlineSlot(today.plusMonths(6), false)))
                .withPracticePreference(Slot.filled(new PracticePreferenceSlot(null, null,
                        "평일 9~18시 회사", "미리미리 하는 편")));
        PlanValidationContext ctx = new PlanValidationContext(today, PlanningStyle.PLANNER, null, 300);
        PlanGenerationRequest request = new PlanGenerationRequest(slots, PlanningStyle.PLANNER, today, 300,
                null, null, List.of(), null);
        GoalPlanValidator validator = new GoalPlanValidator();
        ClaudeGoalAiClient client = client();

        PlanGenerationResult first = client.generatePlan(request);
        PlanValidationResult result = validator.validate(first.plan(), ctx);
        print("1차", first, result);

        GoalPlanDto plan = first.plan();
        if (!result.isValid()) {
            PlanGenerationResult revised = client.generatePlan(request.forRevision(plan, result.hardViolations()));
            result = validator.validate(revised.plan(), ctx);
            print("수정", revised, result);
        }
        assertThat(result.hardViolations()).isEmpty();
    }

    private void print(String label, PlanGenerationResult r, PlanValidationResult v) throws Exception {
        System.out.println("===== " + label + " (tokens in/out=" + r.meta().inputTokens() + "/" + r.meta().outputTokens()
                + ", " + r.meta().latencyMs() + "ms) =====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(r.plan()));
        System.out.println("HARD=" + v.hardViolations());
        System.out.println("SOFT=" + v.softWarnings());
    }
}
