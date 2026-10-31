package com.naleul.naleul.domain.aiUsage.recorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Anthropic 응답에서 model · usage 꺼내기 */
class AnthropicUsageTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void usage_를_꺼낸다() {
        String json = """
                {"id":"msg_1","model":"claude-haiku-4-5-20251001","content":[],
                 "usage":{"input_tokens":512,"output_tokens":300,
                          "cache_creation_input_tokens":10,"cache_read_input_tokens":20}}
                """;
        AnthropicUsage u = AnthropicUsage.parse(om, json);
        assertThat(u).isEqualTo(new AnthropicUsage("claude-haiku-4-5-20251001", 512, 300, 10, 20));
    }

    @Test
    void 캐시_필드가_없으면_0() {
        AnthropicUsage u = AnthropicUsage.parse(om, "{\"model\":\"m\",\"usage\":{\"input_tokens\":1,\"output_tokens\":2}}");
        assertThat(u.cacheCreationInputTokens()).isZero();
        assertThat(u.cacheReadInputTokens()).isZero();
    }

    @Test
    void 해석할_수_없으면_null_이고_예외를_던지지_않는다() {
        assertThat(AnthropicUsage.parse(om, null)).isNull();
        assertThat(AnthropicUsage.parse(om, "not json")).isNull();
        assertThat(AnthropicUsage.parse(om, "{\"model\":\"m\"}")).isNull();
    }
}
