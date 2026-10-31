-- =====================================================================
-- V38: 실제로 한 일 (TimeTable 의 "실제" 블록)
--  계획한 Task 와 상관없이 그 시간에 실제로 한 활동을 기록한다.
--  Task 와 분리한 이유: 실행률·자동 배치·이월·목표 진행 계산에 섞이지 않게.
--  시각은 task.planned_start_at 과 같이 한국 시간 벽시계 값.
-- =====================================================================
CREATE TABLE actual_activity (
    actual_activity_id  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    title               VARCHAR(60)  NOT NULL,
    emoji               VARCHAR(16)  NULL,
    start_at            DATETIME(6)  NOT NULL COMMENT '한국 시간 벽시계 값',
    end_at              DATETIME(6)  NOT NULL COMMENT '한국 시간 벽시계 값',
    goal_category_id    BIGINT       NULL COMMENT '연결한 목표 (선택)',
    replaced_task_id    BIGINT       NULL COMMENT '원래 계획했던 Task (FK 없음 — Task 가 지워져도 기록은 남김)',
    replaced_task_name  VARCHAR(100) NULL COMMENT '원래 계획했던 Task 이름 스냅샷',
    memo                VARCHAR(200) NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (actual_activity_id),
    KEY idx_aa_user_start (user_id, start_at),
    KEY idx_aa_goal (goal_category_id),
    CONSTRAINT fk_aa_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_aa_goal_category FOREIGN KEY (goal_category_id) REFERENCES goal_category (goal_category_id) ON DELETE SET NULL
);
