package com.naleul.naleul.domain.chart.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naleul.naleul.domain.aiUsage.enums.AiFeature;
import com.naleul.naleul.domain.aiUsage.recorder.AiCallRecorder;
import com.naleul.naleul.domain.chart.dto.AiFeedbackDto;
import com.naleul.naleul.domain.user.enums.UserPatternType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ClaudeAiFeedbackClient implements AiFeedbackClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final AiCallRecorder recorder;

    public ClaudeAiFeedbackClient(
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.model}") String model,
            ObjectMapper objectMapper,
            AiCallRecorder recorder
    ) {
        this.model = model;
        this.recorder = recorder;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.anthropic.com/v1/messages")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();
    }

    @Override
    public AiFeedbackDto generateFeedback(UserPatternType patternType, String statsSummary, String periodLabel) {
        String prompt = buildPrompt(patternType, statsSummary, periodLabel);
        String responseBody = null;
        long started = System.currentTimeMillis();

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", 700,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            responseBody = webClient.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(); // ✅ 동기 처리: 여기서 응답 올 때까지 기다림
            recorder.recordSuccess(AiFeature.REPORT_FEEDBACK, responseBody, System.currentTimeMillis() - started);

            return parseResponse(responseBody);

        } catch (WebClientResponseException e) {
            // ⚠️ Anthropic이 4xx/5xx를 줄 때 .block()은 응답 바디를 담기 전에 예외를 던지므로,
            //    responseBody 변수는 항상 null로 찍힘. 실제 이유는 예외 객체 안의
            //    getResponseBodyAsString()에 있음 — 이게 진짜 원인(잘못된 model, 파라미터 등)을 보여줌.
            log.error("AI 피드백 생성 실패 (Anthropic API {} 응답): {}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            recorder.recordFailure(AiFeature.REPORT_FEEDBACK, model, System.currentTimeMillis() - started);
            return fallback(periodLabel);
        } catch (Exception e) {
            log.error("AI 피드백 생성 실패, 기본 피드백으로 대체. responseBody={}", responseBody, e);
            return fallback(periodLabel);
        }
    }

    private AiFeedbackDto fallback(String periodLabel) {
        return AiFeedbackDto.builder()
                .title(periodLabel + " 피드백")
                .content(periodLabel + " 활동 데이터를 기반으로 한 피드백을 준비 중이에요.")
                .build();
    }

    private String buildPrompt(UserPatternType patternType, String statsSummary, String periodLabel) {
        return """
            당신은 자기관리 앱의 AI 코치입니다.
            사용자의 목표 유형은 "%s" 입니다.
            아래는 사용자의 %s 활동 통계입니다:

            %s

            이 유형의 관점에서 사용자의 %s를 피드백해주세요. 다음을 지켜주세요:
            - 위 통계에 나온 구체적인 숫자(시간대, 목표 이름, 퍼센트, 분 단위 변화 등)를 최소 2개 이상 직접 인용하며 설명할 것
            - 잘한 점 1가지와 아쉬운 점 또는 다음에 시도해볼 점 1가지를 모두 포함할 것
            - 뭉뚱그린 일반론("열심히 하셨네요" 같은 말) 대신, 이번 기간 데이터에서만 나올 수 있는 구체적인 이야기를 할 것
            - 4~5문장 분량으로 작성할 것

            반드시 아래 JSON 형식으로만 답변하세요 (다른 설명이나 코드블록 표시 없이):
            {"title": "짧은 제목", "content": "4~5문장 피드백"}
            """.formatted(patternType, periodLabel, statsSummary, periodLabel);
    }

    private AiFeedbackDto parseResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        String text = root.path("content").get(0).path("text").asText();

        // ⚠️ 신규: Claude가 지시를 무시하고 ```json ... ``` 코드펜스로 감싸거나
        //    앞뒤에 설명을 덧붙이는 경우가 있어서, 순수 JSON 부분만 추출.
        String cleaned = extractJson(text);

        JsonNode feedbackJson = objectMapper.readTree(cleaned);

        return AiFeedbackDto.builder()
                .title(feedbackJson.path("title").asText())
                .content(feedbackJson.path("content").asText())
                .build();
    }

    /**
     * 응답 텍스트에서 첫 '{'부터 마지막 '}'까지만 잘라서 순수 JSON을 추출.
     * 코드펜스(```json ... ```)나 앞뒤 설명 문장이 붙어있어도 JSON 객체 부분만 남김.
     */
    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) {
            // JSON 형태를 아예 못 찾으면 원본 그대로 넘겨서 이후 readTree에서 명확한 파싱 에러가 나게 함
            return text;
        }
        return text.substring(start, end + 1);
    }
}