-- =====================================================================
-- V52: 목표 3가지 형태 — 기본 생활 습관 · 업무형 보드 · 목표형 추가 그래프
--
--  1) 기본 생활 습관 (goal_mode = 'HABIT')
--     사용자마다 "기본 생활 습관" 목표가 1개씩 자동으로 생기고(처음 조회할 때), 그 안에
--     영양제 먹기 · 샤워하기 같은 "했는지만 체크하는" 습관을 담는다.
--     - habit        : 습관 (반복 요일 · 알림 시각 · 쉬기)
--     - habit_check  : 그날 했는지 (습관 · 날짜당 1행)
--     - habit_pause  : 쉰 기간 (잔디맵에서 "쉼"으로 보이고 연속 기록 · 실천률에서 빠진다)
--     루틴(routine)을 쓰지 않는 이유: 루틴은 기간 전체의 Task 를 미리 만들어 두는 구조라 종료일 없는 습관과 맞지 않고,
--     자동 배치 · 실행률 통계에 체크 항목이 섞이기 때문.
--
--  2) 업무형 (goal_mode = 'RECORD') 보드
--     - work_topic   : 프로젝트 안의 주제 (기획 · 디자인 QA · 백엔드 …)
--     - task.work_topic_id / work_status / work_memo : 주제 · 보드 칸(시작 전 / 진행 중) · 메모
--       완료는 기존 task_status = 'COMPLETED' 를 그대로 쓴다.
--     업무형은 마감일(goal_category_end_date)을 가질 수 있지만 지나도 자동 완료되지 않는다 (코드에서 처리, 컬럼 변경 없음).
--
--  3) 목표형 (goal_mode = 'ACHIEVEMENT') 추가 그래프
--     메인 수치는 기존 goal_category(metric_*) + goal_metric_log 그대로 두고, 더 만든 그래프만 새 테이블에 담는다.
--     - goal_metric        : 추가 그래프 (이름 · 단위 · 시작값 · 목표값 · 현재값)
--     - goal_metric_value  : 날짜별 기록 (그래프 · 날짜당 1행)
--
--  모두 "추가"만 하므로 지금 배포된 앱(iOS · 웹)은 그대로 동작한다.
-- =====================================================================

-- ── 1. 기본 생활 습관 ─────────────────────────────────────────────

-- 사용자당 기본 생활 습관 그릇은 1개만 (동시에 두 요청이 와도 하나만 생기게).
-- HABIT 이면서 삭제되지 않은 목표일 때만 user_id 가 들어가고, 나머지는 NULL 이라 유니크 검사에서 빠진다.
ALTER TABLE goal_category
    ADD COLUMN habit_home_user_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN goal_mode = 'HABIT' AND goal_category_status <> 'DELETED' THEN user_id END) STORED
        COMMENT 'V52: 기본 생활 습관 그릇 사용자당 1개 보장용 (자동 계산)',
    ADD UNIQUE KEY uk_goal_category_habit_home (habit_home_user_id);

CREATE TABLE habit (
    habit_id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    goal_category_id BIGINT       NOT NULL COMMENT '기본 생활 습관 그릇 (goal_mode = HABIT)',
    habit_name       VARCHAR(30)  NOT NULL,
    emoji            VARCHAR(16)  NULL,
    repeat_days      VARCHAR(80)  NOT NULL DEFAULT '' COMMENT 'MONDAY,WEDNESDAY… 비어 있으면 매일',
    remind_time      TIME         NULL COMMENT '알림 시각 (선택) — 이 시각에 아직 안 했으면 푸시',
    habit_status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE · PAUSED · DELETED',
    sort_order       INT          NOT NULL DEFAULT 0,
    start_date       DATE         NOT NULL COMMENT '이 날부터 체크 대상',
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (habit_id),
    KEY idx_habit_user (user_id, habit_status, sort_order),
    KEY idx_habit_remind (remind_time, habit_status),
    CONSTRAINT fk_habit_goal_category FOREIGN KEY (goal_category_id) REFERENCES goal_category (goal_category_id) ON DELETE CASCADE,
    CONSTRAINT fk_habit_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE habit_check (
    habit_check_id BIGINT      NOT NULL AUTO_INCREMENT,
    habit_id       BIGINT      NOT NULL,
    user_id        BIGINT      NOT NULL,
    check_date     DATE        NOT NULL COMMENT '한국 날짜',
    created_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (habit_check_id),
    UNIQUE KEY uk_habit_check_day (habit_id, check_date),
    KEY idx_habit_check_user (user_id, check_date),
    CONSTRAINT fk_habit_check_habit FOREIGN KEY (habit_id) REFERENCES habit (habit_id) ON DELETE CASCADE,
    CONSTRAINT fk_habit_check_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE habit_pause (
    habit_pause_id BIGINT NOT NULL AUTO_INCREMENT,
    habit_id       BIGINT NOT NULL,
    start_date     DATE   NOT NULL,
    end_date       DATE   NULL COMMENT 'NULL = 아직 쉬는 중',
    PRIMARY KEY (habit_pause_id),
    KEY idx_habit_pause_habit (habit_id, end_date),
    CONSTRAINT fk_habit_pause_habit FOREIGN KEY (habit_id) REFERENCES habit (habit_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── 2. 업무형 보드 ────────────────────────────────────────────────

CREATE TABLE work_topic (
    work_topic_id    BIGINT      NOT NULL AUTO_INCREMENT,
    goal_category_id BIGINT      NOT NULL,
    user_id          BIGINT      NOT NULL,
    topic_name       VARCHAR(30) NOT NULL,
    emoji            VARCHAR(16) NULL,
    sort_order       INT         NOT NULL DEFAULT 0,
    is_deleted       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (work_topic_id),
    KEY idx_work_topic_goal (goal_category_id, is_deleted, sort_order),
    CONSTRAINT fk_work_topic_goal_category FOREIGN KEY (goal_category_id) REFERENCES goal_category (goal_category_id) ON DELETE CASCADE,
    CONSTRAINT fk_work_topic_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE task
    ADD COLUMN work_topic_id BIGINT       NULL COMMENT 'V52: 업무형 주제 (없으면 주제 없음)',
    ADD COLUMN work_status   VARCHAR(20)  NULL COMMENT 'V52: 보드 칸 NOT_STARTED · IN_PROGRESS (완료는 task_status)',
    ADD COLUMN work_memo     VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'V52: 업무 Task 메모',
    ADD KEY idx_task_work_topic (work_topic_id),
    ADD CONSTRAINT fk_task_work_topic FOREIGN KEY (work_topic_id) REFERENCES work_topic (work_topic_id) ON DELETE SET NULL;

-- ── 3. 목표형 추가 그래프 ─────────────────────────────────────────

CREATE TABLE goal_metric (
    goal_metric_id   BIGINT         NOT NULL AUTO_INCREMENT,
    goal_category_id BIGINT         NOT NULL,
    user_id          BIGINT         NOT NULL,
    metric_name      VARCHAR(30)    NOT NULL,
    metric_unit      VARCHAR(10)    NOT NULL DEFAULT '',
    start_value      DECIMAL(10, 2) NOT NULL,
    target_value     DECIMAL(10, 2) NOT NULL,
    current_value    DECIMAL(10, 2) NOT NULL COMMENT '가장 최근 날짜 기록 (없으면 시작값)',
    sort_order       INT            NOT NULL DEFAULT 0,
    is_deleted       BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at       DATETIME(6)    NOT NULL,
    updated_at       DATETIME(6)    NOT NULL,
    PRIMARY KEY (goal_metric_id),
    KEY idx_goal_metric_goal (goal_category_id, is_deleted, sort_order),
    CONSTRAINT fk_goal_metric_goal_category FOREIGN KEY (goal_category_id) REFERENCES goal_category (goal_category_id) ON DELETE CASCADE,
    CONSTRAINT fk_goal_metric_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE goal_metric_value (
    goal_metric_value_id BIGINT         NOT NULL AUTO_INCREMENT,
    goal_metric_id       BIGINT         NOT NULL,
    log_date             DATE           NOT NULL COMMENT '한국 날짜',
    value                DECIMAL(10, 2) NOT NULL,
    memo                 VARCHAR(100)   NULL,
    created_at           DATETIME(6)    NOT NULL,
    updated_at           DATETIME(6)    NOT NULL,
    PRIMARY KEY (goal_metric_value_id),
    UNIQUE KEY uk_gmv_metric_date (goal_metric_id, log_date),
    CONSTRAINT fk_gmv_goal_metric FOREIGN KEY (goal_metric_id) REFERENCES goal_metric (goal_metric_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
