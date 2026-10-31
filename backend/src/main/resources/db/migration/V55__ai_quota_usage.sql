-- ============================================================
-- V55: AI 기능 사용 횟수 (사용자별)
--
-- BM 변경: 목표 · 루틴 · Task · 회고 같은 "만들기" 개수 제한은 없애고, 돈이 드는 AI 기능만 횟수를 센다.
-- 목표 대화(goal_creation_session) · 초안(goal_draft) 은 기존 테이블로 세고,
-- 그 밖의 AI 기능(AI 목표 수정, 브레인 덤프 AI 해석)은 이 테이블에 한 줄씩 남겨 센다.
-- ai_call_log 에는 user_id 가 없어서(비용 집계용) 따로 둔다.
-- ============================================================

CREATE TABLE ai_quota_usage (
    ai_quota_usage_id BIGINT      NOT NULL AUTO_INCREMENT,
    user_id           BIGINT      NOT NULL,
    feature           VARCHAR(30) NOT NULL COMMENT 'GOAL_EDIT, BRAIN_DUMP',
    used_at           DATETIME(6) NOT NULL,
    PRIMARY KEY (ai_quota_usage_id),
    KEY idx_ai_quota_usage_user_feature (user_id, feature, used_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
