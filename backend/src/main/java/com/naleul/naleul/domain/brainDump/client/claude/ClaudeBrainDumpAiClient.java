package com.naleul.naleul.domain.brainDump.client.claude;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.naleul.naleul.domain.aiUsage.enums.AiFeature;
import com.naleul.naleul.domain.aiUsage.recorder.AiCallRecorder;
import com.naleul.naleul.domain.brainDump.client.BrainDumpAiClient;
import com.naleul.naleul.domain.brainDump.client.BrainDumpAiRequest;
import com.naleul.naleul.domain.brainDump.client.BrainDumpAiResult;
import com.naleul.naleul.domain.goalCreation.client.GoalAiException;
import com.naleul.naleul.domain.goalCreation.config.ClaudeGoalAiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Brain dump 해석을 Claude 로 (goal.ai.client=claude 또는 설정 없음).
 * ClaudeGoalAiClient 와 같은 방식: WebClient + block(), 요청 스레드에서 호출 (트랜잭션 밖).
 *
 * 설정 (application.yml, 없으면 기본값)
 *   planner.ai.brain-dump-model            : 비우면 anthropic.model (Haiku)
 *   planner.ai.brain-dump-timeout-seconds  : 15
 *   planner.ai.brain-dump-max-tokens       : 4096
 *   goal.ai.claude.force-tool-use          : 목표 생성과 공유
 *
 * 로그에는 모델·토큰·소요시간만 남기고 사용자 입력 원문은 남기지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "goal.ai", name = "client", havingValue = "claude", matchIfMissing = true)
public class ClaudeBrainDumpAiClient implements BrainDumpAiClient {

    private final WebClient webClient;
    private final ClaudeBrainDumpMapper mapper;
    private final String model;
    private final Duration timeout;
    private final int maxTokens;
    private final boolean forceTool;
    private final AiCallRecorder recorder;

    public ClaudeBrainDumpAiClient(
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.model}") String defaultModel,
            @Value("${planner.ai.brain-dump-model:}") String brainDumpModel,
            @Value("${planner.ai.brain-dump-timeout-seconds:15}") int timeoutSeconds,
            @Value("${planner.ai.brain-dump-max-tokens:4096}") int maxTokens,
            ClaudeGoalAiProperties claudeProperties,
            ObjectMapper objectMapper,
            AiCallRecorder recorder) {
        this.mapper = new ClaudeBrainDumpMapper(objectMapper);
        this.recorder = recorder;
        this.model = brainDumpModel == null || brainDumpModel.isBlank() ? defaultModel : brainDumpModel;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxTokens = maxTokens;
        this.forceTool = claudeProperties.forceToolUse();
        this.webClient = WebClient.builder()
                .baseUrl(claudeProperties.baseUrl())
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }

    @Override
    public BrainDumpAiResult parse(BrainDumpAiRequest request) {
        ObjectNode body = mapper.request(request, model, maxTokens, forceTool);
        long started = System.currentTimeMillis();
        try {
            String response = webClient.post().bodyValue(body).retrieve()
                    .bodyToMono(String.class).timeout(timeout).block();
            long latency = System.currentTimeMillis() - started;
            recorder.recordSuccess(AiFeature.BRAIN_DUMP, response, latency);
            BrainDumpAiResult result = mapper.parse(response, latency);
            log.info("[BrainDumpAi] 성공 model={} items={} inputTokens={} outputTokens={} latencyMs={}",
                    model, request.items().size(), result.meta().inputTokens(), result.meta().outputTokens(), latency);
            return result;
        } catch (GoalAiException e) {
            log.warn("[BrainDumpAi] 응답 해석 실패 model={} reason={} msg={}", model, e.getReason(), e.getMessage());
            throw e;
        } catch (WebClientResponseException e) {
            log.error("[BrainDumpAi] API 오류 model={} status={} body={}", model, e.getStatusCode(), e.getResponseBodyAsString());
            recorder.recordFailure(AiFeature.BRAIN_DUMP, model, System.currentTimeMillis() - started);
            throw new GoalAiException(GoalAiException.Reason.AI_UNAVAILABLE, "Anthropic API " + e.getStatusCode(), e);
        } catch (WebClientRequestException e) {
            log.error("[BrainDumpAi] 네트워크 오류 model={}", model, e);
            recorder.recordFailure(AiFeature.BRAIN_DUMP, model, System.currentTimeMillis() - started);
            throw new GoalAiException(GoalAiException.Reason.AI_UNAVAILABLE, "네트워크 오류", e);
        } catch (RuntimeException e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof TimeoutException) {
                    log.warn("[BrainDumpAi] 타임아웃 model={} timeout={}s", model, timeout.getSeconds());
                    recorder.recordFailure(AiFeature.BRAIN_DUMP, model, System.currentTimeMillis() - started);
                    throw new GoalAiException(GoalAiException.Reason.AI_TIMEOUT, "응답 시간 초과", e);
                }
            }
            log.error("[BrainDumpAi] 알 수 없는 오류 model={}", model, e);
            throw new GoalAiException(GoalAiException.Reason.AI_UNAVAILABLE, "알 수 없는 오류", e);
        }
    }
}
