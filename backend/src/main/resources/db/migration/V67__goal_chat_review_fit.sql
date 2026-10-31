-- AI 채팅 정리 정확도 (2026-10-25) — 운영 대시보드 전용, 사용자 화면 변화 없음
-- 1) 대화 한 턴: 어느 질문의 답인지 · 답 방식(칩/직접/건너뛰기) · 고른 칩 번호 · 이 답에서 AI 가 뽑은 값
ALTER TABLE goal_creation_message
    ADD COLUMN answered_slot  VARCHAR(30) NULL COMMENT 'USER: 어느 질문(슬롯)에 대한 답인지',
    ADD COLUMN answer_source  VARCHAR(10) NULL COMMENT 'USER: CHIP | TYPED | SKIP',
    ADD COLUMN chip_index     INT         NULL COMMENT 'USER: 고른 칩 번호 (0부터)',
    ADD COLUMN extracted      JSON        NULL COMMENT 'USER: 이 답에서 AI 가 뽑은 값 (ExtractedSlots)',
    ADD KEY idx_gcm_created (created_at);   -- 6개월 지난 답 원문 정리 (ChatAnswerRetentionScheduler)

-- 2) 세션: 대화가 끝난 순간의 AI 정리값 · 추천 성향, 정리 화면 진입/첫 계획 만들기 시각, 프롬프트 버전
ALTER TABLE goal_creation_session
    ADD COLUMN ai_slots           JSON        NULL COMMENT '대화가 끝난 순간의 정리값 스냅샷',
    ADD COLUMN ai_planning_style  VARCHAR(20) NULL COMMENT '정리 화면에 AI 추천으로 보인 성향',
    ADD COLUMN review_entered_at  DATETIME(6) NULL COMMENT '정리 화면에 들어온 시각 (인터뷰 종료)',
    ADD COLUMN reviewed_at        DATETIME(6) NULL COMMENT '처음 계획 만들기를 누른 시각',
    ADD COLUMN prompt_version     VARCHAR(20) NULL COMMENT '인터뷰 프롬프트 버전',
    ADD KEY idx_gcs_review_entered (review_entered_at);

-- 3) 정리 화면 항목 결과 — 첫 계획 만들기 때 세션당 7줄
CREATE TABLE goal_review_field (
    goal_review_field_id      BIGINT       NOT NULL AUTO_INCREMENT,
    goal_creation_session_id  BIGINT       NOT NULL,
    user_id                   BIGINT       NOT NULL,
    field                     VARCHAR(30)  NOT NULL COMMENT 'GOAL_STATEMENT · METRIC · DEADLINE · APPROACH · MOTIVATION · PRACTICE_PREFERENCE · PLANNING_STYLE',
    ai_filled                 BOOLEAN      NOT NULL COMMENT 'AI 가 대화에서 값을 채웠는지',
    result                    VARCHAR(10)  NOT NULL COMMENT 'KEPT 그대로 · EDITED 고침 · ADDED 직접 채움 · EMPTY 비운 채로',
    answer_source             VARCHAR(10)  NULL COMMENT '이 항목을 물은 질문에 어떻게 답했는지 (CHIP · TYPED · SKIP, 안 물었으면 NULL)',
    question_message_id       BIGINT       NULL,
    answer_message_id         BIGINT       NULL,
    ai_value                  VARCHAR(500) NULL COMMENT 'AI 가 뽑은 값 (화면 표기, 6개월 뒤 지움)',
    final_value               VARCHAR(500) NULL COMMENT '계획 만들기 때 값 (화면 표기, 6개월 뒤 지움)',
    created_at                DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (goal_review_field_id),
    UNIQUE KEY uk_grf_session_field (goal_creation_session_id, field),
    KEY idx_grf_created (created_at),
    KEY idx_grf_field_created (field, created_at),
    CONSTRAINT fk_grf_session FOREIGN KEY (goal_creation_session_id)
        REFERENCES goal_creation_session (goal_creation_session_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
