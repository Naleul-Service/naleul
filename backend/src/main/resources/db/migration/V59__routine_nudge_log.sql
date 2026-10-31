-- 루틴 실천 알림 발송 기록 (2026-10)
-- 같은 Task 에 같은 알림을 두 번 보내지 않고(uk), 사용자당 하루 개수를 세려고(idx) 남긴다.
CREATE TABLE routine_nudge_log (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    task_id    BIGINT      NOT NULL,
    kind       VARCHAR(30) NOT NULL,
    sent_date  DATE        NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_routine_nudge_task_kind UNIQUE (task_id, kind),
    INDEX idx_routine_nudge_user_date (user_id, sent_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
