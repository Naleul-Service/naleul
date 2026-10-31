// global/policy/PlanLimits.java
package com.naleul.naleul.global.policy;

/**
 * Free/Pro 요금제별 "만들기" 개수 제한.
 *
 * BM 변경 (2026-10): Free 도 기능을 마음껏 써 보도록 만들기 개수 제한은 모두 없앴다 (UNLIMITED).
 * 돈이 드는 AI 기능만 횟수를 센다 → GoalAiProperties (AI 목표 · 초안), AiQuotaPolicy (AI 수정 · 브레인 덤프).
 * 나중에 다시 제한이 필요하면 숫자만 바꾸면 된다 (검사 코드는 그대로 남아 있다).
 */
public final class PlanLimits {

    private PlanLimits() {
        // 상수만 담는 클래스라 인스턴스화 방지
    }

    /** 제한 없음. 화면에 보여줄 때는 display() 로 null 로 바꾼다 */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    // 전체 목표 (GoalCategory)
    public static final int GOAL_CATEGORY_FREE_LIMIT = UNLIMITED;

    // 세부 목표 (GeneralCategory)
    public static final int GENERAL_CATEGORY_FREE_LIMIT = UNLIMITED;

    // 루틴 (Routine)
    public static final int ROUTINE_FREE_LIMIT = UNLIMITED;

    public static final int AUTOBIOGRAPHY_QUESTION_FREE_LIMIT = UNLIMITED;          // CUSTOM 질문 생성 개수
    public static final int AUTOBIOGRAPHY_ANSWER_PER_QUESTION_FREE_LIMIT = UNLIMITED; // 질문당 답변 개수

    public static final int TASK_PER_DAY_FREE_LIMIT = UNLIMITED;
    public static final int IDEA_BOARD_FREE_LIMIT = UNLIMITED;
    public static final int DREAM_BOARD_FREE_LIMIT = UNLIMITED;
    public static final int RETROSPECTIVE_FREE_LIMIT = UNLIMITED;

    // 미션 (동시에 진행하는 ACTIVE 미션 수) — Free 도 Pro 와 같게
    public static final int MISSION_FREE_LIMIT = 3;
    public static final int MISSION_PRO_LIMIT = 3;

    /** 화면용: 제한 없음이면 null */
    public static Integer display(int limit) {
        return limit == UNLIMITED ? null : limit;
    }
}
