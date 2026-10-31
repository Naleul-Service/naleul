package com.naleul.naleul.domain.goalCreation.client.claude;

import com.naleul.naleul.domain.goalCategory.kind.GoalKind;
import com.naleul.naleul.domain.goalCategory.kind.GoalSubType;
import com.naleul.naleul.domain.goalCategory.kind.GoalType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.naleul.naleul.domain.goalCreation.client.*;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.PlanViolation;
import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.goalCreation.dto.slot.GoalSlots;
import com.naleul.naleul.domain.goalCreation.dto.slot.SlotKey;
import com.naleul.naleul.domain.goalCreation.dto.slot.Slot;
import com.naleul.naleul.domain.goalCreation.enums.MessageRole;
import com.naleul.naleul.domain.goalCreation.validator.GoalPlanValidator;
import com.naleul.naleul.domain.goalCreation.validator.PlanValidationContext;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 네트워크 없이 "요청 JSON 만들기"와 "응답 JSON 읽기"만 검증.
 * 응답 샘플은 Anthropic Messages API의 tool_use 응답 형태를 그대로 흉내 낸다.
 */
class ClaudeGoalAiMapperTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);   // 금요일

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final ClaudeGoalAiMapper mapper = new ClaudeGoalAiMapper(objectMapper);

    private InterviewTurnRequest interviewRequest() {
        return new InterviewTurnRequest(
                GoalSlots.empty().withGoalStatement(Slot.filled("몸무게 10kg 감량")),
                List.of(
                        new ConversationMessage(MessageRole.ASSISTANT, "지금 몇 kg이세요?", "metric", false),
                        new ConversationMessage(MessageRole.USER, "78인데 68까지", null, false)),
                4, TODAY, null);
    }

    private String toolUseResponse(String toolName, String inputJson, String stopReason) {
        return """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-test",
                 "content":[{"type":"text","text":"정리할게요."},
                            {"type":"tool_use","id":"toolu_1","name":"%s","input":%s}],
                 "stop_reason":"%s","usage":{"input_tokens":1200,"output_tokens":340}}
                """.formatted(toolName, inputJson, stopReason);
    }

    // ───────────────────── 요청 ─────────────────────

    @Test
    void 인터뷰_요청에_도구정의와_오늘_요일과_대화기록이_들어간다() {
        ObjectNode body = mapper.interviewRequest(interviewRequest(), "claude-test", 1024, false);

        assertThat(body.path("model").asText()).isEqualTo("claude-test");
        assertThat(body.path("tools").get(0).path("name").asText()).isEqualTo("submit_interview_turn");
        assertThat(body.path("tool_choice").path("type").asText()).isEqualTo("auto");
        assertThat(body.path("system").get(0).path("text").asText()).contains("목표 코치");
        assertThat(body.path("system").get(0).path("cache_control").path("type").asText()).isEqualTo("ephemeral");

        String user = body.path("messages").get(0).path("content").asText();
        assertThat(user).contains("2026-10-02 (금)").contains("[남은 질문 수]\n4").contains("사용자: 78인데 68까지");
    }

    @Test
    void forceTool이면_tool_choice로_도구를_강제한다() {
        ObjectNode body = mapper.interviewRequest(interviewRequest(), "claude-test", 1024, true);

        assertThat(body.path("tool_choice").path("type").asText()).isEqualTo("tool");
        assertThat(body.path("tool_choice").path("name").asText()).isEqualTo("submit_interview_turn");
    }

    @Test
    void 수정_요청이면_이전초안과_위반목록이_들어간다() {
        GoalSlots slots = GoalSlots.empty().withGoalStatement(Slot.filled("감량"));
        PlanGenerationRequest first = new PlanGenerationRequest(slots, PlanningStyle.PLANNER, TODAY, 300,
                null, null, List.of(), null);
        GoalPlanDto plan = new FakeGoalAiClient().generatePlan(first).plan();
        PlanGenerationRequest revision = first.forRevision(plan,
                List.of(new PlanViolation("V3-2", "milestones[3].dueDate", "마지막 마일스톤 날짜는 목표 종료일과 같아야 해요.")));

        String user = mapper.planRequest(revision, "claude-test", 8192, false)
                .path("messages").get(0).path("content").asText();

        assertThat(user).contains("[이전 초안]").contains("[고쳐야 할 규칙 위반]").contains("V3-2 milestones[3].dueDate");
    }

    @Test
    void 카테고리를_고르면_그_카테고리_가이드와_사용자_원문이_계획_요청에_들어간다() {
        GoalSlots slots = GoalSlots.empty().withGoalStatement(Slot.filled("2주 3kg 감량"));
        PlanGenerationRequest request = new PlanGenerationRequest(slots, PlanningStyle.PLANNER, TODAY, 300,
                null, null, List.of(), null,
                GoalKind.of(GoalType.HEALTH, GoalSubType.DIET, null),
                List.of("2주 3kg 감량", "헬스장 다니고 무릎이 좀 안 좋아요"));

        String user = mapper.planRequest(request, "m", 10, false).path("messages").get(0).path("content").asText();

        assertThat(user)
                .contains("[목표 카테고리]\n건강 · 다이어트 (goal.subType = DIET)")
                .contains("[카테고리 가이드")
                .contains("저칼로리 식재료 구매")          // 다이어트 가이드의 "만들지 말 것"
                .doesNotContain("금연 체크")               // 다른 카테고리 가이드는 섞이지 않음
                .contains("- 헬스장 다니고 무릎이 좀 안 좋아요");
    }

    @Test
    void 꼭_하고_싶은_일은_인터뷰와_계획_요청에_모두_들어간다() {
        InterviewTurnRequest interview = new InterviewTurnRequest(GoalSlots.empty(), List.of(), 5, TODAY, null,
                null, List.of("헬스", "식단 기록"));
        String interviewText = mapper.interviewRequest(interview, "m", 10, false)
                .path("messages").get(0).path("content").asText();
        assertThat(interviewText).contains("[사용자가 꼭 하겠다고 한 일]").contains("- 헬스").contains("- 식단 기록");

        PlanGenerationRequest plan = new PlanGenerationRequest(GoalSlots.empty(), PlanningStyle.PLANNER, TODAY, 300,
                null, null, List.of(), null, null, List.of(), List.of("헬스"));
        String planText = mapper.planRequest(plan, "m", 10, false)
                .path("messages").get(0).path("content").asText();
        assertThat(planText).contains("[사용자가 꼭 하겠다고 한 일]").contains("- 헬스");
    }

    @Test
    void 꼭_하고_싶은_일이_없으면_섹션이_없다() {
        InterviewTurnRequest interview = new InterviewTurnRequest(GoalSlots.empty(), List.of(), 5, TODAY, null);
        String text = mapper.interviewRequest(interview, "m", 10, false)
                .path("messages").get(0).path("content").asText();
        assertThat(text).doesNotContain("꼭 하겠다고 한 일");
    }

    @Test
    void 카테고리를_고르면_인터뷰에_꼭_물어볼_것이_들어간다() {
        InterviewTurnRequest request = new InterviewTurnRequest(GoalSlots.empty(), List.of(), 5, TODAY, null,
                GoalKind.of(GoalType.HEALTH, GoalSubType.EXERCISE, null));

        String user = mapper.interviewRequest(request, "m", 10, false).path("messages").get(0).path("content").asText();

        assertThat(user).contains("[목표 카테고리]\n건강 · 운동").contains("[이 카테고리에서 꼭 알아야 할 것]").contains("운동 장소");
    }

    // ───────────────────── 응답 ─────────────────────

    @Test
    void 인터뷰_도구응답을_읽고_토큰을_기록한다() {
        String input = """
                {"extracted":{"goalStatement":null,
                              "metric":{"name":"체중","currentValue":78,"targetValue":68,"unit":"kg"},
                              "practicePreference":{"preferredDays":["mon","WED"],"preferredTimeRanges":[{"start":"19:00","end":"22:00"}]},
                              "skippedSlots":[]},
                 "planningStyleSignal":{"style":"planner","evidence":"미리미리"},
                 "nextAction":"ASK","targetSlot":"motivation",
                 "question":"왜 감량하고 싶으세요?","quickReplies":["건강","자신감"],
                 "someNewField":"무시돼야 함"}
                """;

        InterviewTurnResult result = mapper.parseInterview(toolUseResponse("submit_interview_turn", input, "tool_use"), 900);

        assertThat(result.extracted().metric().targetValue()).isEqualByComparingTo(BigDecimal.valueOf(68));
        assertThat(result.extracted().practicePreference().preferredDays()).containsExactly(Weekday.MON, Weekday.WED);
        assertThat(result.planningStyleSignal().style()).isEqualTo(PlanningStyleGuess.PLANNER);   // 소문자도 허용
        assertThat(result.nextAction()).isEqualTo(NextAction.ASK);
        assertThat(result.targetSlot()).isEqualTo(SlotKey.MOTIVATION);
        assertThat(result.meta().inputTokens()).isEqualTo(1200);
        assertThat(result.meta().outputTokens()).isEqualTo(340);
        assertThat(result.meta().latencyMs()).isEqualTo(900);
    }

    @Test
    void 도구를_안쓰고_텍스트로만_답하면_AI_INVALID_OUTPUT() {
        String textOnly = """
                {"model":"claude-test","content":[{"type":"text","text":"좋아요! 몇 kg이세요?"}],
                 "stop_reason":"end_turn","usage":{"input_tokens":10,"output_tokens":5}}
                """;

        assertThatThrownBy(() -> mapper.parseInterview(textOnly, 100))
                .isInstanceOfSatisfying(GoalAiException.class,
                        e -> assertThat(e.getReason()).isEqualTo(GoalAiException.Reason.AI_INVALID_OUTPUT));
    }

    @Test
    void max_tokens로_잘린_응답은_AI_INVALID_OUTPUT() {
        String truncated = toolUseResponse("submit_goal_plan", "{\"goal\":{}}", "max_tokens");

        assertThatThrownBy(() -> mapper.parsePlan(truncated, 100))
                .isInstanceOfSatisfying(GoalAiException.class,
                        e -> assertThat(e.getReason()).isEqualTo(GoalAiException.Reason.AI_INVALID_OUTPUT));
    }

    @Test
    void 계획_도구응답을_GoalPlanDto로_읽으면_서버검증을_통과한다() throws Exception {
        // Fake가 만든 정상 계획을 "AI가 tool input으로 준 것"처럼 감싸서 읽어본다
        GoalSlots slots = GoalSlots.empty().withGoalStatement(Slot.filled("감량"));
        PlanGenerationRequest request = new PlanGenerationRequest(slots, PlanningStyle.PLANNER, TODAY, 300,
                null, null, List.of(), null);
        GoalPlanDto expected = new FakeGoalAiClient().generatePlan(request).plan();
        String input = objectMapper.writeValueAsString(expected);

        PlanGenerationResult result = mapper.parsePlan(toolUseResponse("submit_goal_plan", input, "tool_use"), 30_000);

        assertThat(result.plan().tasks()).hasSameSizeAs(expected.tasks());
        assertThat(result.plan().goal().endDate()).isEqualTo(expected.goal().endDate());
        assertThat(new GoalPlanValidator().validate(result.plan(),
                new PlanValidationContext(TODAY, PlanningStyle.PLANNER, null, 300)).isValid()).isTrue();
    }

    @Test
    void 도구_스키마_리소스가_유효한_JSON이다() {
        JsonNode tool = mapper.planRequest(new PlanGenerationRequest(GoalSlots.empty(), PlanningStyle.PLANNER,
                TODAY, 300, null, null, List.of(), null), "m", 10, false).path("tools").get(0);

        assertThat(tool.path("name").asText()).isEqualTo("submit_goal_plan");
        assertThat(tool.path("input_schema").path("required")).isNotEmpty();
    }
}
