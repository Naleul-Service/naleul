package com.naleul.naleul.domain.goalCreation.client.claude;

import com.naleul.naleul.domain.goalCreation.personal.PersonalPrompt;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.naleul.naleul.domain.goalCreation.client.*;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalEdit.dto.GoalEditOp;
import com.naleul.naleul.domain.scheduleEdit.dto.ScheduleEditOp;
import com.naleul.naleul.domain.goalCreation.dto.plan.PlanViolation;
import com.naleul.naleul.domain.goalCreation.enums.MessageRole;
import com.naleul.naleul.domain.goalCreation.support.GoalPlaybooks;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Claude Messages API 요청 JSON 만들기 / 응답 JSON 해석. (HTTP 호출은 하지 않는 순수 변환 로직)
 *
 * 왜 클라이언트와 분리했나
 *  - HTTP 없이 "요청이 어떻게 만들어지는지", "응답을 어떻게 읽는지"를 단위 테스트할 수 있다.
 *  - 프롬프트(.txt)와 도구 스키마(.json)는 resources/ai/goal-creation/ 에 두어 코드 수정 없이 다듬을 수 있다.
 *
 * 응답 해석이 관대한(lenient) 이유
 *  - AI는 enum을 "planner"처럼 소문자로 주거나, 모르는 값/필드를 섞어 줄 수 있다.
 *  - 대소문자 무시, 모르는 enum → null, 모르는 필드 → 무시로 읽고,
 *    내용이 맞는지는 GoalPlanValidator(서버 검증)가 최종 판단한다.
 */
public class ClaudeGoalAiMapper {

    static final String INTERVIEW_TOOL = "submit_interview_turn";
    static final String PLAN_TOOL = "submit_goal_plan";
    static final String EDIT_TOOL = "submit_goal_edit";
    static final String SCHEDULE_EDIT_TOOL = "submit_schedule_edit";
    private static final String BASE = "ai/goal-creation/";

    private final ObjectMapper writer;     // 프롬프트에 넣을 슬롯/초안 직렬화용 (Spring 설정 그대로)
    private final ObjectMapper lenient;    // AI 응답 해석용
    private final String interviewSystem;
    private final String planSystem;
    private final JsonNode interviewTool;
    private final JsonNode planTool;
    private final String editSystem;
    private final JsonNode editTool;
    private final String scheduleEditSystem;
    private final JsonNode scheduleEditTool;
    private final GoalPlaybooks playbooks;

    public ClaudeGoalAiMapper(ObjectMapper springObjectMapper) {
        this.writer = springObjectMapper;
        this.lenient = JsonMapper.builder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
        this.interviewSystem = readText("interview-system.txt");
        this.planSystem = readText("plan-system.txt");
        this.interviewTool = readJson("interview-tool.json");
        this.planTool = readJson("plan-tool.json");
        this.editSystem = readText("edit-system.txt");
        this.editTool = readJson("edit-tool.json");
        this.scheduleEditSystem = readText("schedule-edit-system.txt");
        this.scheduleEditTool = readJson("schedule-edit-tool.json");
        this.playbooks = new GoalPlaybooks();
    }

    // ───────────────────── 요청 만들기 ─────────────────────

    public ObjectNode interviewRequest(InterviewTurnRequest req, String model, int maxTokens, boolean forceTool) {
        StringBuilder user = new StringBuilder();
        user.append("[오늘]\n").append(dateWithWeekday(req.today())).append("\n\n");
        user.append("[남은 질문 수]\n").append(req.remainingQuestions()).append("\n\n");
        user.append("[현재 슬롯]\n").append(toJson(req.slots())).append("\n\n");
        if (req.kind() != null) {
            user.append("[목표 카테고리]\n").append(req.kind().displayName()).append("\n\n");
            String mustKnow = playbooks.forInterview(req.kind());
            if (mustKnow != null) {
                user.append("[이 카테고리에서 꼭 알아야 할 것]\n").append(mustKnow).append("\n\n");
            }
        }
        if (req.sourceContext() != null) {
            user.append("[기존 목표 정보]\n").append(req.sourceContext()).append("\n\n");
        }
        appendMustDo(user, req.mustDo());
        if (req.capacityNote() != null) {
            user.append("[이 사용자의 실행 기록]\n").append(req.capacityNote()).append("\n\n");
        }
        user.append("[대화 기록]\n");
        for (ConversationMessage m : req.history()) {
            if (m.role() == MessageRole.ASSISTANT) {
                user.append("코치(").append(m.targetSlot()).append(" 질문): ").append(m.content()).append('\n');
            } else {
                user.append("사용자: ").append(m.skip() ? "(건너뛰기)" : m.content()).append('\n');
            }
        }
        user.append("\n위 대화의 마지막 사용자 답변을 반영해 submit_interview_turn 도구로 응답해.");
        return baseRequest(model, maxTokens, interviewSystem, interviewTool, INTERVIEW_TOOL, forceTool, user.toString());
    }

    /**
     * 사용자가 직접 말한 시작일 · 종료일 · 목표 수치를 따로 적어 준다 (슬롯 JSON 안에 묻혀 있으면 AI 가 "안전하게" 바꾸곤 했다).
     * 서버도 GoalDraftWorker 에서 한 번 더 이 값으로 고정한다.
     */
    private void appendUserFixed(StringBuilder user, PlanGenerationRequest req) {
        var slots = req.slots();
        if (slots == null) return;
        StringBuilder fixed = new StringBuilder();
        if (slots.deadline().isFilled() && slots.deadline().value() != null) {
            var d = slots.deadline().value();
            if (d.startDate() != null && d.startDate().isAfter(req.today())) {
                fixed.append("- 시작일: ").append(dateWithWeekday(d.startDate()))
                        .append(" → goal.startDate 이 날. 루틴·Task 는 이 날부터 시작\n");
            }
            if (!d.isFlexible() && d.endDate() != null) {
                fixed.append("- 종료일: ").append(dateWithWeekday(d.endDate())).append(" → goal.endDate 이 날\n");
            }
        }
        if (slots.metric().isFilled() && slots.metric().value() != null && slots.metric().value().targetValue() != null) {
            var m = slots.metric().value();
            fixed.append("- 목표 수치: ").append(m.targetValue().stripTrailingZeros().toPlainString())
                    .append(m.unit() != null ? m.unit() : "")
                    .append(" → goal.metric.targetValue 이 값 그대로 (조정 의견은 aiNote 에만)\n");
        }
        if (!fixed.isEmpty()) {
            user.append("[시작일 · 목표 — 사용자가 정한 값, 바꾸지 마]\n").append(fixed).append('\n');
        }
    }

    public ObjectNode planRequest(PlanGenerationRequest req, String model, int maxTokens, boolean forceTool) {
        StringBuilder user = new StringBuilder();
        user.append("[오늘]\n").append(dateWithWeekday(req.today())).append("\n\n");
        appendUserFixed(user, req);
        user.append("[실행 성향]\n").append(req.planningStyle()).append("\n\n");
        user.append("[루틴 주간 상한").append(req.personal() != null ? " — 이 사용자 기준, 반드시 지켜" : "")
                .append("]\n").append(req.maxWeeklyRoutineMinutes()).append("\n\n");
        if (req.personal() != null) {
            user.append(PersonalPrompt.forPlan(req.personal()));
        }
        user.append("[사용자 정보(인터뷰 슬롯)]\n").append(toJson(req.slots())).append("\n\n");
        if (!req.userAnswers().isEmpty()) {
            user.append("[사용자가 인터뷰에서 한 말]\n");
            req.userAnswers().forEach(a -> user.append("- ").append(a).append('\n'));
            user.append('\n');
        }
        if (req.kind() != null) {
            user.append("[목표 카테고리]\n").append(req.kind().displayName())
                    .append(" (goal.subType = ").append(req.kind().subType()).append(")\n\n");
            String guide = playbooks.forPlan(req.kind());
            if (guide != null) {
                user.append("[카테고리 가이드 — 이 방식대로 설계해]\n").append(guide).append("\n\n");
            }
        } else {
            user.append("[목표 카테고리]\n미지정 — 목표 내용을 보고 goal.subType 을 골라.\n\n");
        }
        if (req.sourceContext() != null) {
            user.append("[기존 목표와 할 일]\n").append(req.sourceContext()).append("\n\n");
        }
        appendMustDo(user, req.mustDo());
        if (req.previousPlan() != null) {
            user.append("[이전 초안]\n").append(toJson(req.previousPlan())).append("\n\n");
        }
        if (req.feedback() != null) {
            user.append("[사용자 피드백]\n").append(req.feedback()).append("\n\n");
        }
        if (!req.violationsToFix().isEmpty()) {
            user.append("[고쳐야 할 규칙 위반]\n");
            for (PlanViolation v : req.violationsToFix()) {
                user.append("- ").append(v.ruleId()).append(" ").append(v.path()).append(": ").append(v.message()).append('\n');
            }
            user.append('\n');
        }
        user.append("위 정보로 계획을 만들어 submit_goal_plan 도구로 응답해.");
        return baseRequest(model, maxTokens, planSystem, planTool, PLAN_TOOL, forceTool, user.toString());
    }

    /** 목표 상세 "말로 고치기" — 지금 목표 상태 + 사용자 문장 → 변경안 */
    public ObjectNode editRequest(GoalEditAiRequest req, String model, int maxTokens, boolean forceTool) {
        String user = "[오늘]\n" + dateWithWeekday(req.today()) + "\n\n"
                + "[지금 목표 상태]\n" + req.goalContext() + "\n\n"
                + "[사용자 요청]\n" + req.instruction() + "\n\n"
                + "위 요청에 필요한 변경만 submit_goal_edit 도구로 응답해.";
        return baseRequest(model, maxTokens, editSystem, editTool, EDIT_TOOL, forceTool, user);
    }

    /** 캘린더 "AI로 일정 조정" — 지금 일정 + 사용자 문장 → 변경안 */
    public ObjectNode scheduleEditRequest(ScheduleEditAiRequest req, String model, int maxTokens, boolean forceTool) {
        String user = "[오늘]\n" + dateWithWeekday(req.today()) + "\n\n"
                + "[지금 일정]\n" + req.context() + "\n\n"
                + "[사용자 요청]\n" + req.instruction() + "\n\n"
                + "위 요청에 필요한 변경만 submit_schedule_edit 도구로 응답해.";
        return baseRequest(model, maxTokens, scheduleEditSystem, scheduleEditTool, SCHEDULE_EDIT_TOOL, forceTool, user);
    }

    /** 시작 화면에서 고른 "꼭 하고 싶은 일" (V42) — 인터뷰·계획 공통 */
    private void appendMustDo(StringBuilder user, List<String> mustDo) {
        if (mustDo == null || mustDo.isEmpty()) return;
        user.append("[사용자가 꼭 하겠다고 한 일]\n");
        mustDo.forEach(m -> user.append("- ").append(m).append('\n'));
        user.append('\n');
    }

    private ObjectNode baseRequest(String model, int maxTokens, String system, JsonNode tool, String toolName,
                                   boolean forceTool, String userContent) {
        ObjectNode body = writer.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        // 프롬프트 캐싱 (2026-10, AI 비용 절감): 도구 정의 + 시스템 지침은 매번 똑같아서 캐시해 둔다.
        //  - 같은 지침을 5분 안에 다시 쓰면(인터뷰 다음 턴 · 계획 수정 요청 · 다시 만들기) 그 부분 입력 비용이 1/10
        //  - 처음 한 번은 1.25배라서, 인터뷰처럼 연달아 부르는 곳에서 크게 남는다
        //  - 캐시 순서는 tools → system → messages 라서 system 끝에 표시하면 도구 정의까지 함께 캐시된다
        ArrayNode systemBlocks = body.putArray("system");
        ObjectNode systemBlock = systemBlocks.addObject();
        systemBlock.put("type", "text");
        systemBlock.put("text", system);
        systemBlock.putObject("cache_control").put("type", "ephemeral");
        ArrayNode tools = body.putArray("tools");
        tools.add(tool.deepCopy());
        ObjectNode toolChoice = body.putObject("tool_choice");
        if (forceTool) {
            toolChoice.put("type", "tool");
            toolChoice.put("name", toolName);
        } else {
            toolChoice.put("type", "auto");
        }
        ArrayNode messages = body.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", userContent);
        return body;
    }

    // ───────────────────── 응답 해석 ─────────────────────

    public InterviewTurnResult parseInterview(String responseBody, long latencyMs) {
        JsonNode root = readTree(responseBody);
        JsonNode input = toolInput(root, INTERVIEW_TOOL);
        if (input instanceof ObjectNode obj) {
            ToolInputRepair.repair(obj, lenient, java.util.Set.of("extracted", "planningStyleSignal"),
                    java.util.Set.of("extracted", "planningStyleSignal", "quickReplies"));
        }
        try {
            InterviewTurnResult raw = lenient.treeToValue(input, InterviewTurnResult.class);
            return new InterviewTurnResult(raw.extracted(), raw.planningStyleSignal(), raw.nextAction(),
                    raw.targetSlot(), raw.question(), raw.quickReplies(), meta(root, latencyMs));
        } catch (IOException | IllegalArgumentException e) {
            throw new GoalAiException(GoalAiException.Reason.AI_INVALID_OUTPUT, "인터뷰 응답 구조 불일치", e);
        }
    }

    public PlanGenerationResult parsePlan(String responseBody, long latencyMs) {
        JsonNode root = readTree(responseBody);
        JsonNode input = toolInput(root, PLAN_TOOL);
        if (input instanceof ObjectNode obj && ToolInputRepair.repair(obj, lenient,
                java.util.Set.of("goal"), java.util.Set.of("goal", "subGoals", "milestones", "tasks"))) {
            org.slf4j.LoggerFactory.getLogger(ClaudeGoalAiMapper.class)
                    .warn("[ClaudeGoalAi] 깨진 계획 도구 입력을 고쳐서 읽음 (문자열로 온 칸 → 객체)");
        }
        try {
            GoalPlanDto plan = lenient.treeToValue(input, GoalPlanDto.class);
            return new PlanGenerationResult(plan, meta(root, latencyMs));
        } catch (IOException | IllegalArgumentException e) {
            throw new GoalAiException(GoalAiException.Reason.AI_INVALID_OUTPUT, "계획 응답 구조 불일치", e);
        }
    }

    public GoalEditAiResult parseEdit(String responseBody, long latencyMs) {
        JsonNode root = readTree(responseBody);
        JsonNode input = toolInput(root, EDIT_TOOL);
        if (input instanceof ObjectNode obj) {
            // 모델이 ops 배열을 문자열로 보내는 경우가 있어 먼저 고쳐 읽어요 (일정 수정과 같은 처리)
            ToolInputRepair.repair(obj, lenient, java.util.Set.of(), java.util.Set.of("ops"));
        }
        try {
            List<GoalEditOp> ops = new ArrayList<>();
            for (JsonNode op : input.path("ops")) {
                try {
                    GoalEditOp parsed = lenient.treeToValue(op, GoalEditOp.class);
                    if (parsed != null && parsed.type() != null) ops.add(parsed);
                } catch (IOException | IllegalArgumentException ignored) {
                    // 변경 하나가 이상해도 나머지는 살린다 (어차피 서버가 다시 검증)
                }
            }
            return new GoalEditAiResult(input.path("summary").asText(null), ops, meta(root, latencyMs));
        } catch (RuntimeException e) {
            throw new GoalAiException(GoalAiException.Reason.AI_INVALID_OUTPUT, "수정 제안 응답 구조 불일치", e);
        }
    }

    public ScheduleEditAiResult parseScheduleEdit(String responseBody, long latencyMs) {
        JsonNode root = readTree(responseBody);
        JsonNode input = toolInput(root, SCHEDULE_EDIT_TOOL);
        if (input instanceof ObjectNode obj) {
            // 모델이 ops 배열을 문자열로 보내는 경우가 있어 먼저 고쳐 읽어요
            ToolInputRepair.repair(obj, lenient, java.util.Set.of(), java.util.Set.of("ops"));
        }
        try {
            List<ScheduleEditOp> ops = new ArrayList<>();
            for (JsonNode op : input.path("ops")) {
                try {
                    ScheduleEditOp parsed = lenient.treeToValue(op, ScheduleEditOp.class);
                    if (parsed != null && parsed.type() != null && parsed.targetId() != null) ops.add(parsed);
                } catch (IOException | IllegalArgumentException ignored) {
                    // 변경 하나가 이상해도 나머지는 살린다 (어차피 서버가 다시 검증)
                }
            }
            return new ScheduleEditAiResult(input.path("summary").asText(null), ops, meta(root, latencyMs));
        } catch (RuntimeException e) {
            throw new GoalAiException(GoalAiException.Reason.AI_INVALID_OUTPUT, "일정 조정 응답 구조 불일치", e);
        }
    }

    /** 응답 content에서 우리 도구의 tool_use 블록 input을 꺼낸다 */
    JsonNode toolInput(JsonNode root, String toolName) {
        String stopReason = root.path("stop_reason").asText();
        if ("max_tokens".equals(stopReason)) {
            // 출력이 잘려서 도구 입력 JSON이 미완성 → 쓸 수 없음. (plan-max-tokens를 늘려야 할 수도 있음)
            throw new GoalAiException(GoalAiException.Reason.AI_INVALID_OUTPUT, "max_tokens로 응답이 잘림");
        }
        for (JsonNode block : root.path("content")) {
            if ("tool_use".equals(block.path("type").asText()) && toolName.equals(block.path("name").asText())) {
                JsonNode input = block.path("input");
                if (input.isObject()) return input;
            }
        }
        throw new GoalAiException(GoalAiException.Reason.AI_INVALID_OUTPUT,
                "도구 호출 없음 (stop_reason=" + stopReason + ")");
    }

    AiCallMeta meta(JsonNode root, long latencyMs) {
        JsonNode usage = root.path("usage");
        return new AiCallMeta(
                root.path("model").asText(null),
                usage.has("input_tokens") ? usage.get("input_tokens").asInt() : null,
                usage.has("output_tokens") ? usage.get("output_tokens").asInt() : null,
                latencyMs);
    }

    // ───────────────────── 내부 ─────────────────────

    /** "2026-10-02 (금)" — 요일이 있어야 "이번 주 토요일" 같은 표현을 AI가 날짜로 바꿀 수 있다 */
    static String dateWithWeekday(LocalDate date) {
        return date + " (" + date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
    }

    private String toJson(Object value) {
        try {
            return writer.writeValueAsString(value);
        } catch (IOException e) {
            throw new GoalAiException(GoalAiException.Reason.AI_INVALID_OUTPUT, "프롬프트 직렬화 실패", e);
        }
    }

    private JsonNode readTree(String body) {
        try {
            return lenient.readTree(body);
        } catch (IOException e) {
            throw new GoalAiException(GoalAiException.Reason.AI_INVALID_OUTPUT, "응답 JSON 파싱 실패", e);
        }
    }

    private String readText(String name) {
        try (InputStream in = new ClassPathResource(BASE + name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // 프롬프트 파일이 빠진 채 배포되면 기동 시점에 바로 실패하게 한다 (조용히 빈 프롬프트로 돌지 않게)
            throw new UncheckedIOException("프롬프트 리소스 로드 실패: " + name, e);
        }
    }

    private JsonNode readJson(String name) {
        try {
            return writer.readTree(readText(name));
        } catch (IOException e) {
            throw new UncheckedIOException("도구 스키마 로드 실패: " + name, e);
        }
    }
}
