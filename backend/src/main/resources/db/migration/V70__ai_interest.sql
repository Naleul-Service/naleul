-- AI 관심도 (2026-10-25) — 운영 대시보드 전용, 사용자 화면 변화 없음
-- AI 기능마다 시도 · 완료 · 중단을 Free / Pro 로 나눠 본다.
-- 1) 시도 당시 플랜: 나중에 Pro 로 바꾼 사람이 Free 때 쓴 기록까지 Pro 로 세지 않도록 시도 순간의 플랜을 남긴다.
--    예전 행은 NULL → 집계 때 지금 플랜으로 대신한다.
ALTER TABLE goal_creation_session
    ADD COLUMN user_plan      VARCHAR(4)  NULL COMMENT '시작 당시 플랜 FREE | PRO',
    ADD COLUMN abandon_reason VARCHAR(20) NULL COMMENT 'USER_CANCEL 직접 그만둠 | EXPIRED 7일 방치 | AI_REJECT 목표로 보기 어려움 | AI_ERROR 시작 실패';

ALTER TABLE ai_suggestion_log
    ADD COLUMN user_plan VARCHAR(4) NULL COMMENT '제안 당시 플랜 FREE | PRO';

-- 2) 한도에 막힌 시도 — 막히면 아무 기록도 남지 않아 "써 보려 했는데 못 쓴" 사람이 안 보였다
CREATE TABLE ai_limit_hit (
    ai_limit_hit_id BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    feature         VARCHAR(30) NOT NULL COMMENT 'GOAL_INTERVIEW | GOAL_PLAN | GOAL_EDIT | SCHEDULE_EDIT',
    reason          VARCHAR(20) NOT NULL COMMENT 'DAILY 하루 한도 | WEEKLY 주간 한도 | REGENERATE 한 대화 다시 만들기 한도',
    user_plan       VARCHAR(4)  NULL     COMMENT '막힌 순간의 플랜 FREE | PRO',
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (ai_limit_hit_id),
    KEY idx_alh_created (created_at),
    KEY idx_alh_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
