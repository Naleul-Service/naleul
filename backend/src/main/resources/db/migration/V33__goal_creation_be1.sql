-- =====================================================================
-- BE-1: AI 대화형 목표 생성 — 기존 구조 확장 (A안)
--   Goal      → goal_category    (컬럼 추가)
--   SubGoal   → general_category (컬럼 추가)
--   Milestone → milestone        (신규)
--   ROUTINE   → routine          (컬럼 추가)
--   ONE_TIME  → task             (컬럼 추가)
--   세션/메시지/초안 → 신규 3개 테이블
--
-- ⚠️ 파일명: V__N__ 의 N을 현재 마지막 Flyway 버전 + 1 로 바꾸세요. (예: V23__goal_creation_be1.sql)
-- ⚠️ users 테이블명 / PK 컬럼명(user_id)이 다르면 맞춰서 수정하세요.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. users: 실행 성향
-- ---------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN planning_style        VARCHAR(20) NULL COMMENT 'PLANNER | SPONTANEOUS',
    ADD COLUMN planning_style_source VARCHAR(20) NULL COMMENT 'AI_INFERRED | USER_SET';

-- ---------------------------------------------------------------------
-- 2. goal_category (= Goal)
--    emoji 컬럼은 기존 테이블 charset과 무관하게 utf8mb4로 명시 (이모지는 4바이트)
-- ---------------------------------------------------------------------
ALTER TABLE goal_category
    ADD COLUMN emoji                VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    ADD COLUMN goal_topic           VARCHAR(20)   NOT NULL DEFAULT 'ETC' COMMENT 'HEALTH | STUDY | CAREER | FINANCE | HOBBY | RELATIONSHIP | LIFE | ETC',
    ADD COLUMN description          VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    ADD COLUMN ai_note              VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    ADD COLUMN is_ai_generated      BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN metric_name          VARCHAR(30)   NULL,
    ADD COLUMN metric_unit          VARCHAR(10)   NULL,
    ADD COLUMN start_value          DECIMAL(10,2) NULL,
    ADD COLUMN current_value        DECIMAL(10,2) NULL,
    ADD COLUMN target_value         DECIMAL(10,2) NULL,
    ADD COLUMN is_deadline_flexible BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN planning_style       VARCHAR(20)   NULL COMMENT '생성 당시 성향 스냅샷';

-- ---------------------------------------------------------------------
-- 3. general_category (= SubGoal)
-- ---------------------------------------------------------------------
ALTER TABLE general_category
    ADD COLUMN emoji             VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    ADD COLUMN description       VARCHAR(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    ADD COLUMN metric_name       VARCHAR(30)   NULL,
    ADD COLUMN metric_unit       VARCHAR(10)   NULL,
    ADD COLUMN metric_comparator VARCHAR(5)    NULL COMMENT 'GTE | LTE | EQ',
    ADD COLUMN metric_period     VARCHAR(10)   NULL COMMENT 'DAILY | WEEKLY | MONTHLY | TOTAL',
    ADD COLUMN target_value      DECIMAL(10,2) NULL,
    ADD COLUMN sort_order        INT           NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- 4. milestone (신규) — goal_category 소속
-- ---------------------------------------------------------------------
CREATE TABLE milestone (
    milestone_id     BIGINT        NOT NULL AUTO_INCREMENT,
    goal_category_id BIGINT        NOT NULL,
    title            VARCHAR(40)   NOT NULL,
    description      VARCHAR(300)  NULL,
    due_date         DATE          NOT NULL,
    target_value     DECIMAL(10,2) NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | ACHIEVED | MISSED',
    achieved_at      DATETIME(6)   NULL,
    sort_order       INT           NOT NULL DEFAULT 0,
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (milestone_id),
    INDEX idx_milestone_goal_due (goal_category_id, due_date),
    CONSTRAINT fk_milestone_goal_category FOREIGN KEY (goal_category_id) REFERENCES goal_category (goal_category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 5. routine (= ROUTINE Task)
--    기존 루틴은 전부 사용자가 직접 만든 것이므로 source 기본값 USER_INPUT
-- ---------------------------------------------------------------------
ALTER TABLE routine
    ADD COLUMN emoji            VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    ADD COLUMN duration_minutes INT         NULL COMMENT 'AI 루틴 소요 시간(분). 시작 시각이 없을 때 스케줄러가 사용',
    ADD COLUMN source           VARCHAR(20) NOT NULL DEFAULT 'USER_INPUT' COMMENT 'AI_GOAL | USER_INPUT';

-- ---------------------------------------------------------------------
-- 6. task (= ONE_TIME Task)
-- ---------------------------------------------------------------------
ALTER TABLE task
    ADD COLUMN milestone_id   BIGINT      NULL,
    ADD COLUMN emoji          VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    ADD COLUMN scheduled_date DATE        NULL COMMENT 'ONE_TIME 권장 실행일',
    ADD COLUMN due_date       DATE        NULL COMMENT 'ONE_TIME 마감일',
    ADD COLUMN source         VARCHAR(20) NOT NULL DEFAULT 'USER_INPUT' COMMENT 'AI_GOAL | USER_INPUT',
    ADD INDEX idx_task_milestone (milestone_id),
    ADD INDEX idx_task_user_due (user_id, due_date),
    ADD CONSTRAINT fk_task_milestone FOREIGN KEY (milestone_id) REFERENCES milestone (milestone_id);

-- ---------------------------------------------------------------------
-- 7. goal_creation_session (신규)
-- ---------------------------------------------------------------------
CREATE TABLE goal_creation_session (
    goal_creation_session_id    BIGINT      NOT NULL AUTO_INCREMENT,
    user_id                     BIGINT      NOT NULL,
    status                      VARCHAR(30) NOT NULL COMMENT 'INTERVIEWING | READY_TO_GENERATE | GENERATING | DRAFT_READY | CONFIRMED | ABANDONED',
    source_goal_category_id     BIGINT      NULL COMMENT '임시 목표 구체화로 시작한 경우',
    question_count              INT         NOT NULL DEFAULT 0,
    slots                       JSON        NOT NULL,
    planning_style_guess        VARCHAR(20) NULL,
    confirmed_goal_category_id  BIGINT      NULL,
    expires_at                  DATETIME(6) NOT NULL,
    created_at                  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (goal_creation_session_id),
    INDEX idx_gcs_user_status (user_id, status),
    INDEX idx_gcs_user_created (user_id, created_at),
    CONSTRAINT fk_gcs_user           FOREIGN KEY (user_id)                    REFERENCES users (user_id),
    CONSTRAINT fk_gcs_source_goal    FOREIGN KEY (source_goal_category_id)    REFERENCES goal_category (goal_category_id),
    CONSTRAINT fk_gcs_confirmed_goal FOREIGN KEY (confirmed_goal_category_id) REFERENCES goal_category (goal_category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 8. goal_creation_message (신규)
-- ---------------------------------------------------------------------
CREATE TABLE goal_creation_message (
    goal_creation_message_id BIGINT      NOT NULL AUTO_INCREMENT,
    goal_creation_session_id BIGINT      NOT NULL,
    role                     VARCHAR(10) NOT NULL COMMENT 'USER | ASSISTANT',
    content                  TEXT        NULL COMMENT '건너뛰기면 NULL',
    target_slot              VARCHAR(30) NULL,
    quick_replies            JSON        NULL,
    is_skip                  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at               DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (goal_creation_message_id),
    INDEX idx_gcm_session (goal_creation_session_id, goal_creation_message_id),
    CONSTRAINT fk_gcm_session FOREIGN KEY (goal_creation_session_id)
        REFERENCES goal_creation_session (goal_creation_session_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 9. goal_draft (신규)
-- ---------------------------------------------------------------------
CREATE TABLE goal_draft (
    goal_draft_id            BIGINT       NOT NULL AUTO_INCREMENT,
    goal_creation_session_id BIGINT       NOT NULL,
    version                  INT          NOT NULL,
    status                   VARCHAR(20)  NOT NULL COMMENT 'GENERATING | READY | FAILED',
    planning_style           VARCHAR(20)  NOT NULL,
    user_feedback            VARCHAR(200) NULL,
    payload                  JSON         NULL,
    error_code               VARCHAR(50)  NULL,
    ai_model                 VARCHAR(60)  NULL,
    input_tokens             INT          NULL,
    output_tokens            INT          NULL,
    created_at               DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at               DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (goal_draft_id),
    UNIQUE KEY uk_gd_session_version (goal_creation_session_id, version),
    CONSTRAINT fk_gd_session FOREIGN KEY (goal_creation_session_id)
        REFERENCES goal_creation_session (goal_creation_session_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
