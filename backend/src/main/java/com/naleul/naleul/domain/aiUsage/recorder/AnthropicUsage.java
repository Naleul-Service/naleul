package com.naleul.naleul.domain.aiUsage.recorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Anthropic Messages API 응답의 model · usage 부분.
 *
 * {"model":"claude-haiku-4-5-20251001", ...,
 *  "usage":{"input_tokens":512,"output_tokens":300,
 *           "cache_creation_input_tokens":0,"cache_read_input_tokens":0}}
 */
public record AnthropicUsage(String model, int inputTokens, int outputTokens,
                             int cacheCreationInputTokens, int cacheReadInputTokens) {

    /** 해석할 수 없으면 null (기록만 건너뛰고 기능은 계속) */
    public static AnthropicUsage parse(ObjectMapper objectMapper, String responseJson) {
        if (responseJson == null || responseJson.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode usage = root.path("usage");
            if (usage.isMissingNode()) return null;
            return new AnthropicUsage(
                    root.path("model").asText(null),
                    usage.path("input_tokens").asInt(0),
                    usage.path("output_tokens").asInt(0),
                    usage.path("cache_creation_input_tokens").asInt(0),
                    usage.path("cache_read_input_tokens").asInt(0));
        } catch (Exception e) {
            return null;
        }
    }
}
