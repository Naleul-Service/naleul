package com.naleul.naleul.domain.goalCreation.client.claude;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.naleul.naleul.domain.aiUsage.enums.AiFeature;
import com.naleul.naleul.domain.aiUsage.recorder.AiCallRecorder;
import com.naleul.naleul.domain.goalCreation.client.*;
import com.naleul.naleul.domain.goalCreation.config.ClaudeGoalAiProperties;
import com.naleul.naleul.domain.goalCreation.config.GoalAiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * 실제 Claude API로 목표 인터뷰/계획을 만드는 구현체 (goal.ai.client=claude 이거나 설정이 없을 때).
 *
 * 기존 ClaudeAiFeedbackClient(리포트)와 같은 WebClient + .block() 동기 방식.
 *  - 인터뷰: 요청 스레드에서 호출 (GoalInterviewService가 트랜잭션 밖에서 부름)
 *  - 계획:   goalPlanExecutor 스레드에서 호출 (GoalDraftWorker)
 *  → 둘 다 이미 "기다려도 되는" 스레드라 block()이 문제 되지 않는다.
 *
 * 실패는 전부 GoalAiException으로 바꿔 던진다. 재시도 여부는 호출하는 서비스가 결정한다
 * (인터뷰: 1회 재시도 후 502 / 계획: 실패 처리). 리포트처럼 기본 문구로 때우지 않는다.
 *
 * 로그 규칙 (명세 8-1): 모델·토큰·소요시간·성공 여부만 INFO로 남기고, 사용자 원문(프롬프트/응답 내용)은 남기지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "goal.ai", name = "client", havingValue = "claude", matchIfMissing = true)
public class ClaudeGoalAiClient implements GoalAiClient {

    private final WebClient webClient;
    private final ClaudeGoalAiMapper mapper;
    private final ClaudeGoalAiProperties claudeProperties;
    private final String interviewModel;
    private final String planModel;
    private final Duration interviewTimeout;
    private final Duration planTimeout;
    private final AiCallRecorder recorder;

    public ClaudeGoalAiClient(
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.model}") String defaultModel,
            GoalAiProperties goalAiProperties,
            ClaudeGoalAiProperties claudeProperties,
            ObjectMapper objectMapper,
            AiCallRecorder recorder
    ) {
        this.mapper = new ClaudeGoalAiMapper(objectMapper);
        this.recorder = recorder;
        this.claudeProperties = claudeProperties;
        this.interviewModel = blankToDefault(goalAiProperties.interviewModel(), defaultModel);
        this.planModel = blankToDefault(goalAiProperties.planModel(), defaultModel);
        this.interviewTimeout = Duration.ofSeconds(goalAiProperties.interviewTimeoutSeconds());
        this.planTimeout = Duration.ofSeconds(goalAiProperties.planTimeoutSeconds());
        this.webClient = WebClient.builder()
                .baseUrl(claudeProperties.baseUrl())
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                // 계획 응답 JSON은 기본 버퍼(256KB) 안이지만, 여유 있게 1MB까지 허용
                .codecs(c -> c.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
        log.info("[ClaudeGoalAi] 활성화 interviewModel={} planModel={} forceToolUse={}",
                interviewModel, planModel, claudeProperties.forceToolUse());
    }

    @Override
    public InterviewTurnResult interviewTurn(InterviewTurnRequest request) {
        ObjectNode body = mapper.interviewRequest(request, interviewModel,
                claudeProperties.interviewMaxTokens(), claudeProperties.forceToolUse());
        return call("interview", AiFeature.GOAL_INTERVIEW, body, interviewTimeout, mapper::parseInterview,
                r -> r.meta());
    }

    @Override
    public PlanGenerationResult generatePlan(PlanGenerationRequest request) {
        ObjectNode body = mapper.planRequest(request, planModel,
                claudeProperties.planMaxTokens(), claudeProperties.forceToolUse());
        boolean first = request.violationsToFix().isEmpty();
        String kind = first ? "plan" : "plan-revision";
        AiFeature feature = first ? AiFeature.GOAL_PLAN : AiFeature.GOAL_PLAN_REVISION;
        try {
            return call(kind, feature, body, planTimeout, mapper::parsePlan, PlanGenerationResult::meta);
        } catch (GoalAiException e) {
            // 응답 모양이 깨져서 못 읽은 경우만 한 번 더 (가끔 생기는 일이라 다시 부르면 대부분 정상)
            if (e.getReason() != GoalAiException.Reason.AI_INVALID_OUTPUT) throw e;
            log.warn("[ClaudeGoalAi] {} 응답을 못 읽어 한 번 더 요청 : {}", kind, e.getMessage());
            return call(kind + "-retry", feature, body, planTimeout, mapper::parsePlan, PlanGenerationResult::meta);
        }
    }

    @Override
    public GoalEditAiResult suggestGoalEdit(GoalEditAiRequest request) {
        // 루틴이 많은 목표는 변경안이 길어 1024×2 토큰에서 잘리거나 40초를 넘겨 "AI 를 쓸 수 없어요"로 실패했다
        // → 토큰 4096 · 시간 55초 (앱 요청 제한 60초보다 짧게)
        ObjectNode body = mapper.editRequest(request, planModel,
                Math.max(claudeProperties.interviewMaxTokens() * 4, 4096), claudeProperties.forceToolUse());
        return call("goal-edit", AiFeature.GOAL_EDIT, body, Duration.ofSeconds(55), mapper::parseEdit, GoalEditAiResult::meta);
    }

    @Override
    public ScheduleEditAiResult suggestScheduleEdit(ScheduleEditAiRequest request) {
        // 2026-10: 변경마다 이유(reason)를 같이 써서 출력이 길어졌어요 — 예전 3072 토큰이면 잘려서 실패했어요
        ObjectNode body = mapper.scheduleEditRequest(request, planModel,
                Math.max(claudeProperties.interviewMaxTokens() * 8, 8192), claudeProperties.forceToolUse());
        return call("schedule-edit", AiFeature.SCHEDULE_EDIT, body, Duration.ofSeconds(55), // 앱 요청 제한 60초 안에
                mapper::parseScheduleEdit, ScheduleEditAiResult::meta);
    }

    // ───────────────────── 공통 호출 ─────────────────────

    @FunctionalInterface
    private interface Parser<T> {
        T parse(String body, long latencyMs);
    }

    private <T> T call(String kind, AiFeature feature, ObjectNode body, Duration timeout, Parser<T> parser, Function<T, AiCallMeta> metaOf) {
        long started = System.currentTimeMillis();
        String model = body.path("model").asText();
        try {
            String response = webClient.post()
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .block();
            long latency = System.currentTimeMillis() - started;
            // 응답을 받았으면 해석 성공 여부와 상관없이 토큰은 과금됐다 → 해석 전에 기록
            recorder.recordSuccess(feature, response, latency);
            T result = parser.parse(response, latency);

            AiCallMeta meta = metaOf.apply(result);
            log.info("[ClaudeGoalAi] {} 성공 model={} inputTokens={} outputTokens={} latencyMs={}",
                    kind, model, meta.inputTokens(), meta.outputTokens(), latency);
            return result;

        } catch (GoalAiException e) {
            log.warn("[ClaudeGoalAi] {} 응답 해석 실패 model={} reason={} msg={} latencyMs={}",
                    kind, model, e.getReason(), e.getMessage(), elapsed(started));
            throw e;
        } catch (WebClientResponseException e) {
            // 4xx/5xx. Anthropic 에러 바디에는 원인(잘못된 model, 파라미터, 과부하 등)만 있고 사용자 원문은 없음
            log.error("[ClaudeGoalAi] {} API 오류 model={} status={} body={} latencyMs={}",
                    kind, model, e.getStatusCode(), e.getResponseBodyAsString(), elapsed(started));
            recorder.recordFailure(feature, model, elapsed(started));
            throw new GoalAiException(GoalAiException.Reason.AI_UNAVAILABLE, "Anthropic API " + e.getStatusCode(), e);
        } catch (WebClientRequestException e) {
            log.error("[ClaudeGoalAi] {} 네트워크 오류 model={} latencyMs={}", kind, model, elapsed(started), e);
            recorder.recordFailure(feature, model, elapsed(started));
            throw new GoalAiException(GoalAiException.Reason.AI_UNAVAILABLE, "네트워크 오류", e);
        } catch (RuntimeException e) {
            // .timeout()이 던지는 TimeoutException은 block()에서 RuntimeException으로 감싸져 나온다
            if (hasCause(e, TimeoutException.class)) {
                log.warn("[ClaudeGoalAi] {} 타임아웃 model={} timeout={}s", kind, model, timeout.getSeconds());
                recorder.recordFailure(feature, model, elapsed(started));
                throw new GoalAiException(GoalAiException.Reason.AI_TIMEOUT, "응답 시간 초과", e);
            }
            log.error("[ClaudeGoalAi] {} 알 수 없는 오류 model={}", kind, model, e);
            throw new GoalAiException(GoalAiException.Reason.AI_UNAVAILABLE, "알 수 없는 오류", e);
        }
    }

    private boolean hasCause(Throwable e, Class<? extends Throwable> type) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) return true;
        }
        return false;
    }

    private long elapsed(long started) {
        return System.currentTimeMillis() - started;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
