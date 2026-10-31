package com.naleul.naleul.domain.pattern.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.naleul.naleul.domain.pattern.client.claude.ClaudePatternMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudePatternMapperTest {

    private final ClaudePatternMapper mapper = new ClaudePatternMapper(new ObjectMapper());

    @Test
    void 요청에_도구와_문장이_들어간다() {
        ObjectNode body = mapper.request("저녁에 강한 꾸준형",
                List.of(new PatternAiClient.Text("weekday", "목요일 실행률이 가장 낮아요.")), "claude-haiku", 1500, false);
        assertThat(body.path("tools").get(0).path("name").asText()).isEqualTo("submit_polished_texts");
        assertThat(body.path("tool_choice").path("type").asText()).isEqualTo("auto");
        assertThat(body.path("messages").get(0).path("content").asText().contains("id=weekday : 목요일 실행률이 가장 낮아요.")).isTrue();
    }

    @Test
    void 도구_응답을_읽는다() {
        String response = """
                {"stop_reason":"tool_use","content":[{"type":"text","text":"..."},
                 {"type":"tool_use","name":"submit_polished_texts","input":{"items":[
                   {"id":"weekday","text":" 목요일 실행률이 가장 낮아요! "},{"id":"x","text":""}]}}]}
                """;
        Map<String, String> out = mapper.parse(response);
        assertThat(out.size()).isEqualTo(1);
        assertThat(out.get("weekday")).isEqualTo("목요일 실행률이 가장 낮아요!");
    }

    @Test
    void 잘린_응답은_예외() {
        boolean thrown = false;
        try {
            mapper.parse("{\"stop_reason\":\"max_tokens\",\"content\":[]}");
        } catch (IllegalStateException e) {
            thrown = true;
        }
        assertThat(thrown).isTrue();
    }
}
