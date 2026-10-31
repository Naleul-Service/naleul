package com.naleul.naleul.domain.goalCreation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * goal.ai.claude.* — 실제 Claude 호출 전용 설정. (모델/타임아웃은 GoalAiProperties에 있음)
 *
 * forceToolUse
 *  - true: tool_choice = {"type":"tool","name":...} 로 도구 사용을 강제
 *  - false(기본): tool_choice = auto + 프롬프트로 "반드시 도구로만 응답" 지시
 *  최신 모델(Opus 5.5, Sonnet 5.5 등)은 도구 강제를 지원하지 않아 400 에러가 나므로 기본값을 false로 둔다.
 *  Haiku 4.5처럼 지원하는 모델이면 true로 켜면 "도구 없이 텍스트로 답하는" 실패가 줄어든다.
 */
@ConfigurationProperties(prefix = "goal.ai.claude")
public record ClaudeGoalAiProperties(
        @DefaultValue("false") boolean forceToolUse,
        @DefaultValue("1024") int interviewMaxTokens,
        @DefaultValue("8192") int planMaxTokens,
        @DefaultValue("https://api.anthropic.com/v1/messages") String baseUrl
) {
}
