-- ============================================================
-- V57: 실제로 한 시간을 여러 구간으로 (한 Task 를 나눠서 한 경우)
--
-- 예) 계획 13:00~18:00 → 실제 13:00~15:00, 16:00~18:00
-- task_actual 은 지금처럼 Task 당 한 줄(첫 시작 ~ 마지막 끝, 소요 시간 = 구간 합계)로 두고,
-- 각 구간은 이 테이블에 따로 남긴다. task_actual 을 여러 줄로 만들면 달성률 · 루틴 소요 시간 집계 쿼리
-- (Task 하나에 task_actual 이 한 줄이라고 가정)가 Task 를 두 번 세게 되기 때문.
-- ============================================================

CREATE TABLE task_actual_segment (
    task_actual_segment_id BIGINT      NOT NULL AUTO_INCREMENT,
    task_actual_id         BIGINT      NOT NULL,
    start_at               DATETIME(6) NOT NULL,
    end_at                 DATETIME(6) NOT NULL,
    PRIMARY KEY (task_actual_segment_id),
    KEY idx_task_actual_segment_actual (task_actual_id, start_at),
    CONSTRAINT fk_task_actual_segment_actual FOREIGN KEY (task_actual_id)
        REFERENCES task_actual (task_actual_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
