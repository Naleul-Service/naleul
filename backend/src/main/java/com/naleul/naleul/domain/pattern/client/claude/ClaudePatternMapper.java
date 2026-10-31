package com.naleul.naleul.domain.pattern.client.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.naleul.naleul.domain.pattern.client.PatternAiClient.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 패턴 문장 다듬기용 Claude 요청 만들기 / 응답 읽기 (HTTP 없음 → 단위 테스트 가능) */
public class ClaudePatternMapper {

    static final String TOOL = "submit_polished_texts";
    private static final String BASE = "ai/pattern/";

    private final ObjectMapper objectMapper;
    private final String system;
    private final JsonNode tool;

    public ClaudePatternMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.system = readText("system.txt");
        try {
            this.tool = objectMapper.readTree(readText("tool.json"));
        } catch (IOException e) {
            throw new UncheckedIOException("pattern tool.json 파싱 실패", e);
        }
    }

    public ObjectNode request(String context, List<Text> texts, String model, int maxTokens, boolean forceTool) {
        StringBuilder u = new StringBuilder();
        if (context != null) u.append("[사용자 실행 스타일]\n").append(context).append("\n\n");
        u.append("[다듬을 문장]\n");
        for (Text t : texts) u.append("- id=").append(t.id()).append(" : ").append(t.text()).append('\n');
        u.append("\n규칙을 지켜 모든 id 를 submit_polished_texts 도구로 돌려줘.");

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

    /** @return id → 문장 (도구 호출이 없거나 잘렸으면 예외) */
    public Map<String, String> parse(String responseBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (IOException e) {
            throw new IllegalStateException("응답 JSON 파싱 실패", e);
        }
        if ("max_tokens".equals(root.path("stop_reason").asText())) {
            throw new IllegalStateException("max_tokens 로 응답이 잘림");
        }
        JsonNode input = null;
        for (JsonNode block : root.path("content")) {
            if ("tool_use".equals(block.path("type").asText()) && TOOL.equals(block.path("name").asText())) {
                input = block.path("input");
            }
        }
        if (input == null || !input.isObject()) throw new IllegalStateException("도구 호출 없음");
        Map<String, String> out = new LinkedHashMap<>();
        for (JsonNode n : input.path("items")) {
            String id = n.path("id").asText(null), text = n.path("text").asText(null);
            if (id != null && text != null && !text.isBlank()) out.put(id, text.trim());
        }
        return out;
    }

    private static String readText(String name) {
        try (InputStream in = ClaudePatternMapper.class.getClassLoader().getResourceAsStream(BASE + name)) {
            if (in == null) throw new IllegalStateException("리소스 없음: " + BASE + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
