-- =====================================================================
-- V34: Task · 캘린더 · TimeBlock 자동 배치 (명세 3, BE-1)
--
--   life_pattern           : 기본 생활 패턴 (수면/점심/저녁/직접 추가한 고정 시간)
--   life_pattern_override  : 특정 날짜에만 바꾸거나 비운 고정 시간
--   planner_setting        : 22시 자동 생성 토글 등 사용자별 설정 (1인 1행)
--   daily_plan             : 날짜별 TimeTable 생성 기록 (UNIQUE = 22시 중복 실행 방지)
--   task                   : 잠금/배치 이유/이월/완료 시각/생성 시각 컬럼
--   goal_category          : 임시 목표 표시
--   general_category       : 목표별 "기타 할 일" 표시
-- =====================================================================

-- 1. 기본 생활 패턴
CREATE TABLE life_pattern (
    life_pattern_id BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    pattern_type    VARCHAR(20)  NOT NULL COMMENT 'SLEEP | LUNCH | DINNER | CUSTOM',
    title           VARCHAR(30)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    emoji           VARCHAR(16)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    start_time      TIME         NOT NULL,
    end_time        TIME         NOT NULL COMMENT 'start_time보다 이르거나 같으면 자정을 넘김 (같은 값은 서버에서 막음)',
    days_mask       INT          NOT NULL DEFAULT 127 COMMENT '월=1 화=2 수=4 목=8 금=16 토=32 일=64, 127=매일',
    sort_order      INT          NOT NULL DEFAULT 0,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (life_pattern_id),
    KEY idx_life_pattern_user (user_id),
    CONSTRAINT fk_life_pattern_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

-- 2. 패턴 예외 (그날만 변경 / 비우기)
CREATE TABLE life_pattern_override (
    life_pattern_override_id BIGINT      NOT NULL AUTO_INCREMENT,
    life_pattern_id          BIGINT      NOT NULL,
    user_id                  BIGINT      NOT NULL,
    target_date              DATE        NOT NULL COMMENT '블록이 시작하는 날짜',
    start_time               TIME        NULL,
    end_time                 TIME        NULL,
    skipped                  BOOLEAN     NOT NULL DEFAULT FALSE COMMENT 'TRUE면 그날 블록 없음',
    created_at               DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (life_pattern_override_id),
    UNIQUE KEY uk_override_pattern_date (life_pattern_id, target_date),
    KEY idx_override_user_date (user_id, target_date),
    CONSTRAINT fk_override_pattern FOREIGN KEY (life_pattern_id) REFERENCES life_pattern (life_pattern_id) ON DELETE CASCADE,
    CONSTRAINT fk_override_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

-- 3. 플래너 설정 (사용자당 1행, 없으면 서비스가 기본값으로 생성)
CREATE TABLE planner_setting (
    user_id               BIGINT      NOT NULL,
    auto_generate_enabled BOOLEAN     NOT NULL DEFAULT TRUE  COMMENT '22시 내일 TimeTable 자동 생성',
    nightly_push_enabled  BOOLEAN     NOT NULL DEFAULT TRUE  COMMENT '22시 생성 완료 푸시 (Free 포함)',
    task_gap_minutes      INT         NOT NULL DEFAULT 10    COMMENT '자동 배치 시 Task 사이 간격(분)',
    pattern_initialized   BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '기본 패턴 생성 여부 (모두 지워도 다시 만들지 않기 위함)',
    updated_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_planner_setting_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

-- 4. 일일 계획
CREATE TABLE daily_plan (
    daily_plan_id  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    plan_date      DATE         NOT NULL,
    source         VARCHAR(20)  NOT NULL COMMENT 'NIGHTLY | BRAIN_DUMP | REPLAN | GOAL_CREATED',
    status         VARCHAR(20)  NOT NULL COMMENT 'GENERATING | GENERATED | REVIEWED | FAILED',
    placed_count   INT          NOT NULL DEFAULT 0,
    carried_count  INT          NOT NULL DEFAULT 0,
    unplaced_count INT          NOT NULL DEFAULT 0,
    summary        VARCHAR(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    generated_at   DATETIME(6)  NULL,
    reviewed_at    DATETIME(6)  NULL,
    PRIMARY KEY (daily_plan_id),
    UNIQUE KEY uk_daily_plan_user_date (user_id, plan_date),
    CONSTRAINT fk_daily_plan_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

-- 5. task 컬럼 추가
ALTER TABLE task
    ADD COLUMN schedule_locked        BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '사용자가 정한 시간 → 자동 배치 제외',
    ADD COLUMN placed_by              VARCHAR(10)  NULL COMMENT 'USER | ENGINE',
    ADD COLUMN placement_reason       VARCHAR(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    ADD COLUMN placement_reason_codes VARCHAR(200) NULL COMMENT '쉼표로 구분한 이유 코드',
    ADD COLUMN task_kind              VARCHAR(20)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL | APPOINTMENT',
    ADD COLUMN carry_over_count       INT          NOT NULL DEFAULT 0,
    ADD COLUMN completed_at           DATETIME(6)  NULL COMMENT '완료 버튼을 누른 시각 (한국 시간)',
    ADD COLUMN created_at             DATETIME(6)  NULL COMMENT 'Free 일일 생성 한도 집계용 (기존 데이터는 NULL)';

CREATE INDEX idx_task_user_routine_date  ON task (user_id, routine_date);
CREATE INDEX idx_task_user_planned_start ON task (user_id, planned_start_at);
CREATE INDEX idx_task_user_created       ON task (user_id, created_at);

-- 기존 데이터: 시간이 정해진 Task는 사용자가 정한 시간으로 보고 잠근다 (자동 배치가 옮기지 않도록)
UPDATE task
SET schedule_locked = TRUE,
    placed_by       = 'USER'
WHERE planned_start_at IS NOT NULL;

-- 6. 임시 목표 / 기타 할 일
ALTER TABLE goal_category
    ADD COLUMN is_temporary BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Brain dump에서 AI가 만든 임시 목표';

ALTER TABLE general_category
    ADD COLUMN is_default_bucket BOOLEAN NOT NULL DEFAULT FALSE COMMENT '목표별 "기타 할 일" 세부 목표';
