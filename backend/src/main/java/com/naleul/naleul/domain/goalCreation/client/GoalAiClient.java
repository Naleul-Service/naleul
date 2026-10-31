package com.naleul.naleul.domain.goalCreation.client;

/**
 * 목표 생성용 AI 클라이언트.
 *
 * 구현체
 *  - FakeGoalAiClient   : goal.ai.client=fake  (로컬/테스트, AI 호출 없이 고정 규칙으로 응답)
 *  - ClaudeGoalAiClient : goal.ai.client=claude (운영, BE-7에서 구현)
 *
 * 서비스는 이 인터페이스만 의존하므로, 구현체를 바꿔도 서비스 코드는 그대로다.
 * 기존 리포트용 AiFeedbackClient(domain.chart.client)와 같은 패턴.
 *
 * 실패 시 GoalAiException을 던진다. (AiFeedbackClient처럼 fallback 값을 돌려주지 않는 이유:
 * 목표 계획은 대체 문구로 때울 수 없고, 서비스가 재시도/FAILED 처리를 결정해야 하기 때문)
 */
public interface GoalAiClient {

    /** 인터뷰 1턴 (동기, 짧은 응답) */
    InterviewTurnResult interviewTurn(InterviewTurnRequest request);

    /** 계획 초안 생성 또는 수정 (비동기 작업 안에서 호출됨, 긴 응답) */
    PlanGenerationResult generatePlan(PlanGenerationRequest request);

    /**
     * 이미 만든 목표를 한 문장으로 고치기 — 바꿀 항목 목록(변경안)만 만든다. 적용은 사용자가 확인한 뒤 서버가 한다.
     * 구현하지 않은 클라이언트는 "지원 안 함"으로 실패한다.
     */
    default GoalEditAiResult suggestGoalEdit(GoalEditAiRequest request) {
        throw new GoalAiException(GoalAiException.Reason.AI_UNAVAILABLE, "목표 수정 제안을 지원하지 않는 클라이언트");
    }

    /**
     * 캘린더 "AI로 일정 조정" — 여러 목표의 루틴 · Task 를 한 문장으로 옮기는 변경안만 만든다 (적용은 사용자가 확인한 뒤).
     */
    default ScheduleEditAiResult suggestScheduleEdit(ScheduleEditAiRequest request) {
        throw new GoalAiException(GoalAiException.Reason.AI_UNAVAILABLE, "일정 조정 제안을 지원하지 않는 클라이언트");
    }
}
