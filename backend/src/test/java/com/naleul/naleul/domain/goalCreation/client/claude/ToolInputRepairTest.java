package com.naleul.naleul.domain.goalCreation.client.claude;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ToolInputRepairTest {

    final ObjectMapper json = new ObjectMapper();
    final Set<String> roots = Set.of("goal", "subGoals", "milestones", "tasks");

    @Test
    void 도구_문법_문자열로_온_goal_을_객체로_고친다() throws Exception {
        ObjectNode root = (ObjectNode) json.readTree("""
            {"goal": "<parameter name=\\"title\\">생존 일본어 회화 익히기</parameter>\\n<parameter name=\\"emoji\\">🇯🇵</parameter>\\n<parameter name=\\"startDate\\">2026-10-29</parameter>\\n<parameter name=\\"metric\\">null</parameter>\\n<parameter name=\\"tasks\\">[{\\"tempId\\":\\"t1\\"}]</parameter>",
             "subGoals": [], "milestones": []}
            """);

        assertThat(ToolInputRepair.repair(root, json, Set.of("goal"), roots)).isTrue();

        assertThat(root.get("goal").get("title").asText()).isEqualTo("생존 일본어 회화 익히기");
        assertThat(root.get("goal").get("emoji").asText()).isEqualTo("🇯🇵");
        assertThat(root.get("goal").get("startDate").asText()).isEqualTo("2026-10-29");
        assertThat(root.get("goal").get("metric").isNull()).isTrue();
        assertThat(root.get("tasks").get(0).get("tempId").asText()).isEqualTo("t1");   // 맨 위로 올림
    }

    @Test
    void JSON_문자열로_온_칸도_고친다() throws Exception {
        ObjectNode root = (ObjectNode) json.readTree("""
            {"goal": "{\\"title\\":\\"A\\"}", "tasks": ["{\\"tempId\\":\\"t1\\"}"]}
            """);
        assertThat(ToolInputRepair.repair(root, json, Set.of("goal"), roots)).isTrue();
        assertThat(root.get("goal").get("title").asText()).isEqualTo("A");
        assertThat(root.get("tasks").get(0).get("tempId").asText()).isEqualTo("t1");
    }

    @Test
    void 정상_입력은_그대로() throws Exception {
        ObjectNode root = (ObjectNode) json.readTree("{\"goal\":{\"title\":\"A\"},\"tasks\":[]}");
        assertThat(ToolInputRepair.repair(root, json, Set.of("goal"), roots)).isFalse();
    }
}
