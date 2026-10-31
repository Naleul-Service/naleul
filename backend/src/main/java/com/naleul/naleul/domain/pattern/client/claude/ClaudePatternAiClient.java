package com.naleul.naleul.domain.pattern.client.claude;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.naleul.naleul.domain.aiUsage.enums.AiFeature;
import com.naleul.naleul.domain.aiUsage.recorder.AiCallRecorder;
import com.naleul.naleul.domain.goalCreation.config.ClaudeGoalAiProperties;
import com.naleul.naleul.domain.pattern.client.PatternAiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 패턴 문장 다듬기를 Claude 로 (goal.ai.client=claude 또는 설정 없음). ClaudeBrainDumpAiClient 와 같은 방식.
 *
 * 설정 (application.yml, 없으면 기본값)
 *   pattern.ai.model            : 비우면 anthropic.model (Haiku)
 *   pattern.ai.timeout-seconds  : 10
 *   pattern.ai.max-tokens       : 1500
 *
 * 로그에는 모델·소요시간만 남기고 문장 원문은 남기지 않는다.
 * 실패는 예외로 던지고, PatternTextPolisher 가 잡아서 템플릿 문장을 쓴다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "goal.ai", name = "client", havingValue = "claude", matchIfMissing = true)
public class ClaudePatternAiClient implements PatternAiClient {

    private final WebClient webClient;
    private final ClaudePatternMapper mapper;
    private final String model;
    private final Duration timeout;
    private final int maxTokens;
    private final boolean forceTool;
    private final AiCallRecorder recorder;

    public ClaudePatternAiClient(
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.model}") String defaultModel,
            @Value("${pattern.ai.model:}") String patternModel,
            @Value("${pattern.ai.timeout-seconds:10}") int timeoutSeconds,
            @Value("${pattern.ai.max-tokens:1500}") int maxTokens,
            ClaudeGoalAiProperties claudeProperties,
            ObjectMapper objectMapper,
            AiCallRecorder recorder) {
        this.mapper = new ClaudePatternMapper(objectMapper);
        this.recorder = recorder;
        this.model = patternModel == null || patternModel.isBlank() ? defaultModel : patternModel;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxTokens = maxTokens;
        this.forceTool = claudeProperties.forceToolUse();
        this.webClient = WebClient.builder()
                .baseUrl(claudeProperties.baseUrl())
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(512 * 1024))
                .build();
    }

    @Override
    public Map<String, String> polish(String context, List<Text> texts) {
        ObjectNode body = mapper.request(context, texts, model, maxTokens, forceTool);
        long started = System.currentTimeMillis();
        String response;
        try {
            response = webClient.post().bodyValue(body).retrieve()
                    .bodyToMono(String.class).timeout(timeout).block();
        } catch (RuntimeException e) {
            // 실패 처리(템플릿 문장으로 대체)는 PatternTextPolisher 가 한다. 여기서는 실패 기록만 남기고 그대로 던진다
            recorder.recordFailure(AiFeature.PATTERN_POLISH, model, System.currentTimeMillis() - started);
            throw e;
        }
        recorder.recordSuccess(AiFeature.PATTERN_POLISH, response, System.currentTimeMillis() - started);
        Map<String, String> result = mapper.parse(response);
        log.info("[PatternAi] 성공 model={} texts={} returned={} latencyMs={}",
                model, texts.size(), result.size(), System.currentTimeMillis() - started);
        return result;
    }
}
