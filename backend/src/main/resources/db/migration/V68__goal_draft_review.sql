-- AI 초안 정확도 (2026-10-25) — 운영 대시보드 전용, 사용자 화면 · 앱 변화 없음
-- 확정 전: AI 최신 초안 ↔ 확정한 계획을 칸 단위로 비교
-- 확정 뒤: 첫 행동(루틴 체크 · Task 완료 · 실제 한 일 추가 · 수치 기록)부터 14일 · 30일 뒤 다시 비교 + 그 기간 루틴 실천률

-- 1) 초안마다 어떤 요청으로 만들어졌는지 + 말로 고치기 직전에 손으로 고친 칸
ALTER TABLE goal_draft
    ADD COLUMN source     VARCHAR(20) NULL COMMENT 'INITIAL 첫 초안 · REGEN 다시 생성 · QUICK_FIX 말로 고치기 · STYLE 성향 변경 · INTENSITY 강도 변경 · RETRY 실패 뒤 다시 시도',
    ADD COLUMN base_edits JSON        NULL COMMENT '말로 고치기 직전 사용자가 손으로 고친 칸 목록 (DraftPlanComparer.Diff)';

-- 2) 확정한 세션 1줄
CREATE TABLE goal_draft_review (
    goal_draft_review_id      BIGINT       NOT NULL AUTO_INCREMENT,
    goal_creation_session_id  BIGINT       NOT NULL,
    goal_category_id          BIGINT       NOT NULL,
    user_id                   BIGINT       NOT NULL,
    path                      VARCHAR(20)  NOT NULL COMMENT '가장 강한 신호: REGEN 다시 생성 > QUICK_FIX 말로 고치기 > MANUAL 직접 수정 > AS_IS 그대로 확정',
    draft_count               INT          NOT NULL COMMENT '만든 초안 수 (실패 포함, 재시도 제외)',
    regen_count               INT          NOT NULL,
    quick_fix_count           INT          NOT NULL,
    style_change_count        INT          NOT NULL,
    intensity_change_count    INT          NOT NULL,
    regen_after_quick_fix     BOOLEAN      NOT NULL COMMENT '말로 고치다가 다시 생성으로 넘어갔는지',
    base_edit_count           INT          NOT NULL DEFAULT 0 COMMENT '말로 고치기 직전에 손으로 고친 칸 수 합',
    ai_fields                 INT          NOT NULL COMMENT 'AI 가 만든 칸 수 (자동 조정 칸 제외)',
    kept_fields               INT          NOT NULL,
    edited_fields             INT          NOT NULL,
    deleted_fields            INT          NOT NULL,
    added_items               INT          NOT NULL,
    auto_fields               INT          NOT NULL COMMENT '사용자가 다른 값을 바꿔서 자동으로 다시 맞춰진 칸',
    intensity                 VARCHAR(20)  NULL,
    planning_style            VARCHAR(20)  NULL,
    prompt_version            VARCHAR(20)  NULL COMMENT '계획 프롬프트 버전 (PlanPromptVersion)',
    draft_weekly_minutes      INT          NOT NULL COMMENT 'AI 초안 루틴의 일주일 총 시간(분)',
    confirmed_weekly_minutes  INT          NOT NULL COMMENT '확정한 루틴의 일주일 총 시간(분)',
    first_draft_at            DATETIME(6)  NULL,
    confirmed_at              DATETIME(6)  NOT NULL,
    first_action_at           DATETIME(6)  NULL COMMENT '확정 뒤 그 목표의 첫 행동 시각',
    first_action_type         VARCHAR(20)  NULL COMMENT 'TASK_DONE · ACTUAL · METRIC',
    d14_checked_at            DATETIME(6)  NULL,
    d14_routine_planned       INT          NULL COMMENT '첫 행동부터 14일 동안 해야 했던 루틴',
    d14_routine_done          INT          NULL,
    d30_checked_at            DATETIME(6)  NULL,
    d30_routine_planned       INT          NULL,
    d30_routine_done          INT          NULL,
    created_at                DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (goal_draft_review_id),
    UNIQUE KEY uk_gdr_session (goal_creation_session_id),
    UNIQUE KEY uk_gdr_goal (goal_category_id),
    KEY idx_gdr_confirmed (confirmed_at),
    KEY idx_gdr_first_action (first_action_at),
    CONSTRAINT fk_gdr_session FOREIGN KEY (goal_creation_session_id)
        REFERENCES goal_creation_session (goal_creation_session_id) ON DELETE CASCADE,
    CONSTRAINT fk_gdr_goal FOREIGN KEY (goal_category_id)
        REFERENCES goal_category (goal_category_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 3) 항목(목표 · 루틴 · 일회성 Task · 세부 목표 · 마일스톤) 1줄
CREATE TABLE goal_draft_review_item (
    goal_draft_review_item_id BIGINT       NOT NULL AUTO_INCREMENT,
    goal_draft_review_id      BIGINT       NOT NULL,
    item_kind                 VARCHAR(20)  NOT NULL COMMENT 'GOAL · ROUTINE · ONE_TIME · SUB_GOAL · MILESTONE',
    item_key                  VARCHAR(40)  NOT NULL COMMENT '초안 tempId (목표는 goal)',
    label                     VARCHAR(100) NULL COMMENT '항목 이름 (화면 표기, 6개월 뒤 지움)',
    result                    VARCHAR(10)  NOT NULL COMMENT '확정 때: KEPT 그대로 · EDITED 고침 · DELETED 삭제 · ADDED 사용자 추가',
    entity_id                 BIGINT       NULL COMMENT '저장된 routine_id · task_id · goal_category_id (확정 뒤 비교용)',
    d14_result                VARCHAR(10)  NULL COMMENT '첫 행동부터 14일 뒤: KEPT · EDITED · DELETED',
    d14_planned               INT          NULL,
    d14_done                  INT          NULL,
    d30_result                VARCHAR(10)  NULL,
    d30_planned               INT          NULL,
    d30_done                  INT          NULL,
    PRIMARY KEY (goal_draft_review_item_id),
    KEY idx_gdri_review (goal_draft_review_id),
    CONSTRAINT fk_gdri_review FOREIGN KEY (goal_draft_review_id)
        REFERENCES goal_draft_review (goal_draft_review_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 4) 칸 1줄 (확정 때 + 말로 고치기 직전)
CREATE TABLE goal_draft_review_field (
    goal_draft_review_field_id BIGINT       NOT NULL AUTO_INCREMENT,
    goal_draft_review_id       BIGINT       NOT NULL,
    goal_draft_review_item_id  BIGINT       NULL COMMENT '확정 때 칸만 (말로 고치기 직전 칸은 NULL)',
    stage                      VARCHAR(20)  NOT NULL COMMENT 'CONFIRM 확정 때 · BEFORE_QUICK_FIX 말로 고치기 직전',
    item_kind                  VARCHAR(20)  NOT NULL,
    field                      VARCHAR(20)  NOT NULL COMMENT 'TITLE · EMOJI · START_DATE · END_DATE · METRIC_START · METRIC_TARGET · DESCRIPTION · DAYS · START_TIME · DURATION · HOW_TO · SUB_GOAL · MILESTONE · SCHEDULED_DATE · DUE_DATE · TARGET_VALUE · ITEM',
    result                     VARCHAR(10)  NOT NULL COMMENT 'KEPT · EDITED · DELETED · ADDED · AUTO(자동 조정)',
    direction                  VARCHAR(10)  NULL COMMENT 'EARLIER 당김 · LATER 늦춤/미룸 · MORE 늘림 · LESS 줄임 · CHANGED 바꿈',
    delta                      INT          NULL COMMENT '크기 (날짜=일, 시각 · 걸리는 시간=분, 요일=개수)',
    ai_value                   VARCHAR(300) NULL COMMENT 'AI 값 (화면 표기, 6개월 뒤 지움)',
    final_value                VARCHAR(300) NULL COMMENT '최종 값 (화면 표기, 6개월 뒤 지움)',
    d14_result                 VARCHAR(10)  NULL COMMENT '확정 뒤 14일: KEPT · EDITED · DELETED',
    d30_result                 VARCHAR(10)  NULL,
    created_at                 DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (goal_draft_review_field_id),
    KEY idx_gdrf_review (goal_draft_review_id),
    KEY idx_gdrf_item (goal_draft_review_item_id),
    KEY idx_gdrf_created (created_at),
    CONSTRAINT fk_gdrf_review FOREIGN KEY (goal_draft_review_id)
        REFERENCES goal_draft_review (goal_draft_review_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
