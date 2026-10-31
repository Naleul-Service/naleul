-- =====================================================================
-- V35: 나의 패턴 (명세 4)
--  1. task_schedule_log        : Task 일정 변경 기록 (AI 배치 수정률, 자주 옮기는 블록, 이월 놓침)
--  2. brain_dump_log           : Brain dump 확정 기록 (주 n회, 한 번 평균 n개, 주로 추가하는 때)
--  3. pattern_report           : 패턴 리포트 캐시 (사용자 · 기간 · 기준일당 1행)
--  4. pattern_suggestion_action: 제안 반영 / 미루기 기록
-- =====================================================================

-- 1. Task 일정 변경 기록
CREATE TABLE task_schedule_log (
    task_schedule_log_id BIGINT      NOT NULL AUTO_INCREMENT,
    user_id              BIGINT      NOT NULL,
    task_id              BIGINT      NOT NULL,
    routine_id           BIGINT      NULL COMMENT '루틴 Task면 루틴 (자주 옮기는 블록 묶음용)',
    action               VARCHAR(20) NOT NULL COMMENT 'ENGINE_PLACE | USER_MOVE | USER_UNSCHEDULE | CARRY_OVER',
    from_date            DATE        NULL,
    from_start_at        DATETIME    NULL COMMENT '한국 시간 벽시계 값',
    to_date              DATE        NULL,
    to_start_at          DATETIME    NULL COMMENT '한국 시간 벽시계 값',
    created_at           DATETIME(6) NOT NULL COMMENT '한국 시간',
    PRIMARY KEY (task_schedule_log_id),
    KEY idx_tsl_user_created (user_id, created_at),
    KEY idx_tsl_user_action_from (user_id, action, from_date),
    KEY idx_tsl_task (task_id),
    CONSTRAINT fk_tsl_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
    -- task FK 없음: Task 가 물리 삭제돼도 기록은 남겨 통계에 쓴다
);

-- 2. Brain dump 확정 기록
CREATE TABLE brain_dump_log (
    brain_dump_log_id BIGINT      NOT NULL AUTO_INCREMENT,
    user_id           BIGINT      NOT NULL,
    item_count        INT         NOT NULL,
    temp_goal_count   INT         NOT NULL DEFAULT 0,
    confirmed_at      DATETIME(6) NOT NULL COMMENT '한국 시간',
    PRIMARY KEY (brain_dump_log_id),
    KEY idx_bdl_user_confirmed (user_id, confirmed_at),
    CONSTRAINT fk_bdl_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

-- 3. 패턴 리포트 캐시
CREATE TABLE pattern_report (
    pattern_report_id BIGINT      NOT NULL AUTO_INCREMENT,
    user_id           BIGINT      NOT NULL,
    period            VARCHAR(20) NOT NULL COMMENT 'RECENT_4W | ALL',
    base_date         DATE        NOT NULL COMMENT '계산 기준일(오늘, 한국 시간). 날짜가 바뀌면 새로 계산',
    payload_json      MEDIUMTEXT  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '응답 본문 (AI 문장 포함)',
    ai_status         VARCHAR(20) NOT NULL COMMENT 'AI | TEMPLATE',
    created_at        DATETIME(6) NOT NULL COMMENT '한국 시간',
    PRIMARY KEY (pattern_report_id),
    UNIQUE KEY uk_pattern_report (user_id, period, base_date),
    KEY idx_pattern_report_base_date (base_date),
    CONSTRAINT fk_pattern_report_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

-- 4. 제안 반영 / 미루기 기록
CREATE TABLE pattern_suggestion_action (
    pattern_suggestion_action_id BIGINT       NOT NULL AUTO_INCREMENT,
    user_id                      BIGINT       NOT NULL,
    suggestion_key               VARCHAR(100) NOT NULL COMMENT '예: ROUTINE_TIME:31:12:40',
    suggestion_type              VARCHAR(20)  NOT NULL COMMENT 'ROUTINE_TIME | ROUTINE_DAY',
    routine_id                   BIGINT       NULL,
    action                       VARCHAR(20)  NOT NULL COMMENT 'APPLIED | DISMISSED',
    before_json                  VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    after_json                   VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    created_at                   DATETIME(6)  NOT NULL COMMENT '한국 시간',
    PRIMARY KEY (pattern_suggestion_action_id),
    KEY idx_psa_user_key (user_id, suggestion_key),
    CONSTRAINT fk_psa_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);
