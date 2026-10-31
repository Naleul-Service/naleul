package com.naleul.naleul.domain.aiUsage.quota;

import com.naleul.naleul.domain.aiUsage.enums.AiFeature;

/**
 * AI 기능별 횟수 (BM, 2026-10).
 *
 * 원칙: "만들기" 개수는 제한하지 않는다. 돈이 드는 AI 만 센다.
 *       Free 도 핵심 AI 를 매일 충분히 경험할 수 있게 넉넉히 주고, 더 쓰고 싶으면 Pro.
 *
 * | 기능               | Free            | Pro      |
 * |--------------------|-----------------|----------|
 * | AI 목표 만들기      | 하루 1 · 주 3    | 하루 10   |  ← GoalAiProperties (goal_creation_session 으로 셈)
 * | AI 계획 초안        | 하루 5          | 하루 20   |  ← GoalAiProperties (goal_draft 로 셈, 목표 하나에 5번까지)
 * | AI 목표 수정(말로)  | 하루 3          | 하루 30   |  ← 여기
 * | 브레인 덤프 AI 해석 | 하루 3          | 하루 30   |  ← 여기 (넘으면 규칙 해석으로 계속 쓸 수 있음)
 * | AI 일정 조정(캘린더)| 하루 3          | 하루 30   |  ← 여기
 *
 * 패턴 문장 다듬기 · 주간 리포트 피드백은 서버가 주 1회 알아서 부르는 것이라 사용자 한도가 없다.
 */
public final class AiQuotaPolicy {

    private AiQuotaPolicy() {
    }

    public static int dailyLimit(AiFeature feature, boolean pro) {
        return switch (feature) {
            case GOAL_EDIT, BRAIN_DUMP, SCHEDULE_EDIT -> pro ? 30 : 3;
            default -> Integer.MAX_VALUE;
        };
    }
}
