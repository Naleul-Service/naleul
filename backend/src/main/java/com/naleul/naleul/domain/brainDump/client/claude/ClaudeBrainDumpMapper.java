package com.naleul.naleul.domain.brainDump.client.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.naleul.naleul.domain.brainDump.client.BrainDumpAiRequest;
import com.naleul.naleul.domain.brainDump.client.BrainDumpAiResult;
import com.naleul.naleul.domain.brainDump.enums.DateType;
import com.naleul.naleul.domain.brainDump.enums.GoalMatchType;
import com.naleul.naleul.domain.goalCreation.client.AiCallMeta;
import com.naleul.naleul.domain.goalCreation.client.GoalAiException;
import com.naleul.naleul.domain.task.enums.TaskKind;
import com.naleul.naleul.domain.task.enums.TaskPriority;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Brain dump 용 Claude 요청 만들기 / 응답 읽기 (HTTP 없음 → 단위 테스트 가능).
 * 응답은 필드 하나하나를 관대하게 읽는다: 형식이 틀린 값은 null 로 두고, 최종 판단은 서버 검증(Sanitizer)이 한다.
 */
public class ClaudeBrainDumpMapper {

    static final String TOOL = "submit_brain_dump";
    private static final String BASE = "ai/brain-dump/";

    private final ObjectMapper objectMapper;
    private final String system;
    private final JsonNode tool;

    public ClaudeBrainDumpMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.system = readText("system.txt");
        try {
            this.tool = objectMapper.readTree(readText("tool.json"));
        } catch (IOException e) {
            throw new UncheckedIOException("brain-dump tool.json 파싱 실패", e);
        }
    }

    public ObjectNode request(BrainDumpAiRequest req, String model, int maxTokens, boolean forceTool) {
        StringBuilder u = new StringBuilder();
        u.append("[오늘]\n").append(req.today()).append(" (")
                .append(req.today().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN)).append(")\n\n");
        u.append("[목표 목록]\n");
        if (req.goals().isEmpty()) u.append("(없음)\n");
        for (BrainDumpAiRequest.GoalContext g : req.goals()) {
            u.append("- id=").append(g.goalCategoryId()).append(" ")
                    .append(g.emoji() != null ? g.emoji() + " " : "").append(g.name())
                    .append(g.temporary() ? " (임시 목표)" : "").append('\n');
            for (BrainDumpAiRequest.SubGoal s : g.subGoals()) {
                u.append("    · 세부 목표 id=").append(s.generalCategoryId()).append(" ").append(s.name()).append('\n');
            }
            for (BrainDumpAiRequest.MilestoneContext m : g.milestones()) {
                u.append("    · 마일스톤 id=").append(m.milestoneId()).append(" ").append(m.title())
                        .append(m.dueDate() != null ? " (" + m.dueDate() + "까지)" : "").append('\n');
            }
        }
        u.append("\n[할 일 입력]\n");
        for (BrainDumpAiRequest.Item i : req.items()) {
            u.append("- clientKey=").append(i.clientKey()).append(" : ").append(i.text()).append('\n');
        }
        u.append("\n모든 줄을 해석해 submit_brain_dump 도구로 응답해.");

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", system);
        ArrayNode tools = body.putArray("tools");
        tools.add(tool.deepCopy());
        ObjectNode choice = body.putObject("tool_choice");
        if (forceTool) {
            choice.put("type", "tool");
            choice.put("name", TOOL);
        } else {
            choice.put("type", "auto");
        }
        ObjectNode msg = body.putArray("messages").addObject();
        msg.put("role", "user");
        msg.put("content", u.toString());
        return body;
    }

    public BrainDumpAiResult parse(String responseBody, long latencyMs) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (IOException e) {
            throw new GoalAiException(GoalAiException.Reason.AI_INVALID_OUTPUT, "응답 JSON 파싱 실패", e);
        }
        if ("max_tokens".equals(root.path("stop_reason").asText())) {
            throw new GoalAiException(GoalAiException.Reason.AI_INVALID_OUTPUT, "max_tokens로 응답이 잘림");
        }
        JsonNode input = null;
        for (JsonNode block : root.path("content")) {
            if ("tool_use".equals(block.path("type").asText()) && TOOL.equals(block.path("name").asText())) {
                input = block.path("input");
            }
        }
        if (input == null || !input.isObject()) {
            throw new GoalAiException(GoalAiException.Reason.AI_INVALID_OUTPUT, "도구 호출 없음");
        }

        List<BrainDumpAiResult.Item> items = new ArrayList<>();
        for (JsonNode n : input.path("items")) {
            JsonNode g = n.path("goal");
            BrainDumpAiResult.Goal goal = g.isObject()
                    ? new BrainDumpAiResult.Goal(enumOf(GoalMatchType.class, g.path("type")), longOf(g.path("goalCategoryId")),
                    longOf(g.path("generalCategoryId")), longOf(g.path("milestoneId")), textOf(g.path("tempKey")), textOf(g.path("reason")))
                    : null;
            items.add(new BrainDumpAiResult.Item(
                    textOf(n.path("clientKey")), textOf(n.path("title")),
                    enumOf(DateType.class, n.path("dateType")), dateOf(n.path("date")),
                    timeOf(n.path("startTime")), timeOf(n.path("endTime")),
                    n.path("estimatedMinutes").isNumber() ? n.path("estimatedMinutes").asInt() : null,
                    enumOf(TaskPriority.class, n.path("priority")), enumOf(TaskKind.class, n.path("kind")), goal));
        }
        List<BrainDumpAiResult.TempGoal> temps = new ArrayList<>();
        for (JsonNode n : input.path("tempGoals")) {
            temps.add(new BrainDumpAiResult.TempGoal(textOf(n.path("tempKey")), textOf(n.path("name")), textOf(n.path("emoji"))));
        }
        JsonNode usage = root.path("usage");
        AiCallMeta meta = new AiCallMeta(root.path("model").asText(null),
                usage.has("input_tokens") ? usage.get("input_tokens").asInt() : null,
                usage.has("output_tokens") ? usage.get("output_tokens").asInt() : null, latencyMs);
        return new BrainDumpAiResult(items, temps, meta);
    }

    // ── 관대한 값 읽기 ──

    private static String textOf(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() ? null : n.asText();
    }

    private static Long longOf(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) return null;
        if (n.isNumber()) return n.asLong();
        try {
            return Long.parseLong(n.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate dateOf(JsonNode n) {
        try {
            return n == null || !n.isTextual() ? null : LocalDate.parse(n.asText().trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LocalTime timeOf(JsonNode n) {
        try {
            return n == null || !n.isTextual() || n.asText().isBlank() ? null : LocalTime.parse(n.asText().trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, JsonNode n) {
        if (n == null || !n.isTextual()) return null;
        try {
            return Enum.valueOf(type, n.asText().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String readText(String name) {
        try (InputStream in = new ClassPathResource(BASE + name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 리소스 로드 실패: " + name, e);
        }
    }
}
