package com.naleul.naleul.domain.goalCreation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * application.yml의 goal.ai.* 설정.
 * 값이 yml에 없으면 @DefaultValue가 쓰이므로, 운영 yml을 당장 안 고쳐도 기동에 문제 없음.
 */
@ConfigurationProperties(prefix = "goal.ai")
public record GoalAiProperties(
        @DefaultValue("claude") String client,                    // fake | claude (로컬은 yml에서 fake로)
        String interviewModel,                                    // null이면 anthropic.model 사용 (BE-7)
        String planModel,                                         // null이면 anthropic.model 사용 (BE-7)
        @DefaultValue("20") int interviewTimeoutSeconds,
        @DefaultValue("120") int planTimeoutSeconds,
        @DefaultValue("5") int maxQuestions,
        @DefaultValue("420") int maxWeeklyRoutineMinutes,
        @DefaultValue("3") int maxRegenerationsPerSession,   // 2026-10: AI 비용 — 한 대화에서 처음 + 다시 만들기 2번
        // BM(2026-10): Free 는 AI 목표 만들기 하루 1개 · 주 3개, 초안은 하루 5번 (목표 하나 + 다시 만들기 4번)
        @DefaultValue("1") int dailySessionLimit,
        @DefaultValue("5") int dailyDraftLimit,
        @DefaultValue("7") int sessionExpireDays,
        @DefaultValue("5") int draftZombieMinutes,
        // ── Pro · 지정 사용자 한도 (무료는 위 daily-session-limit / daily-draft-limit) ──
        @DefaultValue("10") int proDailySessionLimit,
        @DefaultValue("20") int proDailyDraftLimit,
        // Pro 한도를 주는 사용자 id (운영진·테스트 계정). 예) GOAL_AI_PRO_USER_IDS=1,2
        @DefaultValue({"1", "2"}) List<Long> proUserIds,
        // Free 의 주간 AI 목표 만들기 한도 (월요일 0시, 한국 시간에 다시 채워짐). Pro 는 주간 한도 없음
        @DefaultValue("3") int weeklySessionLimit
) {
    // ⚠️ 생성자를 하나 더 만들면 안 된다: @ConfigurationProperties record 는 생성자가 하나일 때만
    //    생성자 바인딩을 한다. 둘이 되면 기본 생성자를 찾다가 기동 실패 ("No default constructor found").

    /** Pro 이거나 지정 사용자면 Pro 한도 */
    public boolean proLimitFor(Long userId, boolean premium) {
        return premium || (proUserIds != null && proUserIds.contains(userId));
    }

    /** 하루에 시작할 수 있는 AI 목표 대화 수 */
    public int sessionLimitFor(Long userId, boolean premium) {
        return proLimitFor(userId, premium) ? proDailySessionLimit : dailySessionLimit;
    }

    /** 이번 주(월~일)에 시작할 수 있는 AI 목표 대화 수. Pro 면 제한 없음(Integer.MAX_VALUE) */
    public int weeklySessionLimitFor(Long userId, boolean premium) {
        return proLimitFor(userId, premium) ? Integer.MAX_VALUE : weeklySessionLimit;
    }

    /** 하루에 만들 수 있는 AI 초안 수 (다시 생성 포함) */
    public int draftLimitFor(Long userId, boolean premium) {
        return proLimitFor(userId, premium) ? proDailyDraftLimit : dailyDraftLimit;
    }
}
