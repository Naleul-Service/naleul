-- =====================================================================
-- V47: 개인 맞춤 목표 설계
--  1. goal_outcome  : 목표가 끝났을 때의 결과 — 달성 / 부분 달성 / 미달성 + 그때의 실천률 스냅샷
--                     지금까지는 끝까지 해낸 목표와 기간만 지난 목표가 똑같이 COMPLETED 라 구분할 수 없었다.
--                     같은 카테고리 "달성자 익명 집계"(계획 AI 의 출발점)의 재료가 된다.
--  2. goal_plan_fit : AI 목표를 확정할 때의 개인 맞춤 정보(강도·개인 상한·실행률)와
--                     AI 초안을 사용자가 얼마나 고쳤는지 — 개인 맞춤이 효과가 있는지 재는 용도
-- =====================================================================
CREATE TABLE goal_outcome (
    goal_outcome_id        BIGINT      NOT NULL AUTO_INCREMENT,
    goal_category_id       BIGINT      NOT NULL,
    user_id                BIGINT      NOT NULL,
    goal_type              VARCHAR(20) NULL COMMENT 'HEALTH | LEARNING | HABIT | ETC | WORK',
    goal_sub_type          VARCHAR(30) NULL COMMENT 'EXERCISE, DIET … (GoalSubType)',
    result                 VARCHAR(20) NOT NULL COMMENT 'ACHIEVED 달성 · PARTIAL 부분 달성 · NOT_ACHIEVED 미달성',
    source                 VARCHAR(10) NOT NULL COMMENT 'USER 직접 고름 · AUTO 기간이 끝나 자동 판정',
    planned_days           INT         NULL COMMENT '시작일 ~ 원래 종료일',
    actual_days            INT         NULL COMMENT '시작일 ~ 실제로 끝낸 날',
    metric_percent         INT         NULL COMMENT '수치 목표 진행률 % (수치 없으면 NULL)',
    routine_rate           INT         NULL COMMENT '루틴 실천률 % (해야 했던 루틴 중 한 비율)',
    task_rate              INT         NULL COMMENT 'Task 실천률 %',
    routine_count          INT         NOT NULL DEFAULT 0,
    kept_routine_count     INT         NOT NULL DEFAULT 0 COMMENT '실천률 60% 이상으로 유지된 루틴 수',
    weekly_routine_minutes INT         NOT NULL DEFAULT 0 COMMENT '루틴 주간 합계(분)',
    ai_generated           BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at             DATETIME(6) NOT NULL,
    updated_at             DATETIME(6) NOT NULL,
    PRIMARY KEY (goal_outcome_id),
    UNIQUE KEY uk_go_goal (goal_category_id),
    KEY idx_go_sub_type_result (goal_sub_type, result),
    KEY idx_go_user (user_id),
    CONSTRAINT fk_go_goal_category FOREIGN KEY (goal_category_id) REFERENCES goal_category (goal_category_id) ON DELETE CASCADE,
    CONSTRAINT fk_go_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

CREATE TABLE goal_plan_fit (
    goal_plan_fit_id        BIGINT      NOT NULL AUTO_INCREMENT,
    goal_category_id        BIGINT      NOT NULL,
    user_id                 BIGINT      NOT NULL,
    intensity               VARCHAR(20) NOT NULL COMMENT 'STEADY 안정형 · CHALLENGE 도전형',
    based_on_history        BOOLEAN     NOT NULL COMMENT '실행 기록으로 맞췄는지 (false 면 인터뷰 답변 기준)',
    execution_rate          INT         NULL COMMENT '확정 당시 최근 4주 실행률 %',
    weekly_cap_minutes      INT         NOT NULL COMMENT '개인 루틴 주간 상한',
    draft_weekly_minutes    INT         NOT NULL COMMENT 'AI 초안의 루틴 주간 합계',
    confirmed_weekly_minutes INT        NOT NULL COMMENT '확정한 계획의 루틴 주간 합계',
    draft_task_count        INT         NOT NULL,
    edited_task_count       INT         NOT NULL COMMENT '추가·삭제·이름·요일·시간이 바뀐 Task 수',
    created_at              DATETIME(6) NOT NULL,
    PRIMARY KEY (goal_plan_fit_id),
    UNIQUE KEY uk_gpf_goal (goal_category_id),
    KEY idx_gpf_user (user_id),
    CONSTRAINT fk_gpf_goal_category FOREIGN KEY (goal_category_id) REFERENCES goal_category (goal_category_id) ON DELETE CASCADE,
    CONSTRAINT fk_gpf_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);
