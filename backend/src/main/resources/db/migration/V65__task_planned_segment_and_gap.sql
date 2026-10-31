-- =====================================================================
-- V65: TimeTable 자동 배치 규칙 (2026-10)
--  1. task_planned_segment : 연속된 빈 시간이 없어 고정 시간(점심 등) 앞뒤로 나눠 넣은 Task 의 계획 구간
--       예) 4시간 공부 · 빈 시간 09~12시, 점심 12~13시, 13~15시 → 09:00~12:00 + 13:00~14:00
--     task.planned_start_at / planned_end_at 은 지금처럼 첫 시작 ~ 마지막 끝, planned_duration_minutes 는 구간 합계.
--     Task 를 여러 줄로 나누면 실천률 · 루틴 회차 집계가 두 번 세게 되므로 구간만 따로 남긴다 (V57 task_actual_segment 와 같은 방식).
--     사용자가 블록을 옮기거나 시간 미정으로 돌리면 구간은 지운다 (계획 시각과 안 맞는 구간은 화면에서도 무시).
--  2. Task 사이 쉬는 시간 기본값 10분 → 20분 (20~30분이 적당하다는 피드백). 기본값 그대로 쓰던 사용자만 바꾼다.
-- =====================================================================
CREATE TABLE task_planned_segment (
    task_planned_segment_id BIGINT      NOT NULL AUTO_INCREMENT,
    task_id                 BIGINT      NOT NULL,
    start_at                DATETIME(6) NOT NULL COMMENT '한국 시간 벽시계 값 (planned_start_at 과 같은 기준)',
    end_at                  DATETIME(6) NOT NULL,
    sort_order              INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (task_planned_segment_id),
    KEY idx_task_planned_segment_task (task_id, sort_order),
    CONSTRAINT fk_task_planned_segment_task FOREIGN KEY (task_id) REFERENCES task (task_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE task
    ADD COLUMN planned_split BOOLEAN NOT NULL DEFAULT FALSE COMMENT '나눠 배치됨 — 구간은 task_planned_segment, planned_duration_minutes 는 구간 합계';

ALTER TABLE planner_setting
    MODIFY COLUMN task_gap_minutes INT NOT NULL DEFAULT 20 COMMENT '자동 배치 시 Task 사이 쉬는 시간(분)';

UPDATE planner_setting SET task_gap_minutes = 20 WHERE task_gap_minutes = 10;
