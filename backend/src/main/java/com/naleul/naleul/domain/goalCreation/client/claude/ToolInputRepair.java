package com.naleul.naleul.domain.goalCreation.client.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 도구 입력이 가끔 깨져서 오는 경우를 고친다.
 *
 * 왜: 도구 스키마가 크면 모델이 객체 칸(goal 등)에 JSON 객체 대신
 *     "<parameter name=\"title\">일본어 회화</parameter><parameter name=\"emoji\">🇯🇵</parameter>…" 같은
 *     도구 호출 문법 문자열을 넣는 일이 있다 (운영 로그: PlanGoal 을 String 에서 만들 수 없음 → "계획을 만들지 못했어요").
 *     뒤 칸(subGoals · tasks)까지 그 문자열 안에 같이 들어가기도 한다.
 *
 * 고치는 것
 *  - 객체/배열이어야 할 칸이 "{...}" / "[...]" 문자열 → JSON 으로 다시 읽기
 *  - <parameter name="x">값</parameter> 문자열 → {x: 값} (값도 JSON 이면 JSON 으로)
 *    그중 맨 위 칸 이름(subGoals 등)은 맨 위로 올린다 (이미 있으면 그대로)
 */
final class ToolInputRepair {

    private static final Pattern PARAM = Pattern.compile(
            "<parameter name=\"([A-Za-z0-9_]+)\">(.*?)(?:</parameter>|(?=<parameter name=)|$)", Pattern.DOTALL);

    private ToolInputRepair() {
    }

    /**
     * @param objectFields 객체여야 하는 칸 (예: goal)
     * @param rootFields   맨 위 칸 이름 (예: goal, subGoals, milestones, tasks)
     * @return 고쳤으면 true
     */
    static boolean repair(ObjectNode root, ObjectMapper json, Set<String> objectFields, Set<String> rootFields) {
        boolean changed = false;
        for (String field : rootFields) {
            JsonNode v = root.get(field);
            if (v == null || !v.isTextual()) continue;
            String text = v.asText().strip();
            JsonNode parsed = tryJson(json, text);
            if (parsed != null && (parsed.isObject() || parsed.isArray())) {
                root.set(field, parsed);
                changed = true;
                continue;
            }
            if (!text.contains("<parameter name=")) continue;
            ObjectNode obj = json.createObjectNode();
            Matcher m = PARAM.matcher(text);
            while (m.find()) {
                String key = m.group(1);
                String raw = m.group(2).strip();
                JsonNode value = tryJson(json, raw);
                JsonNode node = value != null ? value : TextNode.valueOf(raw);
                if (rootFields.contains(key) && !key.equals(field)) {
                    // 뒤 칸이 이 문자열 안으로 빨려 들어온 경우 → 맨 위로 (비어 있을 때만)
                    JsonNode existing = root.get(key);
                    if (existing == null || existing.isNull() || existing.isTextual()
                            || (existing.isArray() && existing.isEmpty())) {
                        root.set(key, node);
                    }
                } else {
                    obj.set(key, node);
                }
            }
            if (!obj.isEmpty()) {
                root.set(field, objectFields.contains(field) ? obj : obj);
                changed = true;
            }
        }
        // 배열 칸 안의 원소가 문자열 JSON 인 경우 (tasks: ["{...}", ...])
        for (String field : rootFields) {
            JsonNode v = root.get(field);
            if (v == null || !v.isArray()) continue;
            for (int i = 0; i < v.size(); i++) {
                JsonNode item = v.get(i);
                if (!item.isTextual()) continue;
                JsonNode parsed = tryJson(json, item.asText().strip());
                if (parsed != null && parsed.isObject()) {
                    ((com.fasterxml.jackson.databind.node.ArrayNode) v).set(i, parsed);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static JsonNode tryJson(ObjectMapper json, String text) {
        if (text.isEmpty()) return null;
        char c = text.charAt(0);
        if (c != '{' && c != '[' && c != '"' && c != '-' && !Character.isDigit(c)
                && !text.equals("true") && !text.equals("false") && !text.equals("null")) {
            return null;
        }
        try {
            return json.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }
}
