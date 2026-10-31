-- =====================================================================
-- V36: 목표 수치 기록 (목표 상세 "지금 어디쯤?" 도표)
--  예) "2주 동안 3kg 감량" 목표에서 사용자가 매일 입력하는 오늘 몸무게.
--  목표 · 날짜당 1행 (같은 날 다시 기록하면 덮어씀).
--  goal_category.current_value 는 "가장 최근 날짜의 기록"으로 맞춰진다.
-- =====================================================================
CREATE TABLE goal_metric_log (
    goal_metric_log_id BIGINT         NOT NULL AUTO_INCREMENT,
    goal_category_id   BIGINT         NOT NULL,
    user_id            BIGINT         NOT NULL,
    log_date           DATE           NOT NULL COMMENT '한국 날짜',
    value              DECIMAL(10, 2) NOT NULL,
    memo               VARCHAR(100)   NULL,
    created_at         DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    PRIMARY KEY (goal_metric_log_id),
    UNIQUE KEY uk_gml_goal_date (goal_category_id, log_date),
    KEY idx_gml_user (user_id),
    CONSTRAINT fk_gml_goal_category FOREIGN KEY (goal_category_id) REFERENCES goal_category (goal_category_id) ON DELETE CASCADE,
    CONSTRAINT fk_gml_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

-- 기존 수치 목표: 시작값과 다른 현재 값이 이미 있으면 오늘 기록 1건으로 옮겨 도표에 점이 하나는 찍히게 한다
INSERT INTO goal_metric_log (goal_category_id, user_id, log_date, value, memo, created_at, updated_at)
SELECT gc.goal_category_id, gc.user_id, DATE(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+09:00')), gc.current_value,
       NULL, NOW(6), NOW(6)
FROM goal_category gc
WHERE gc.current_value IS NOT NULL
  AND gc.start_value IS NOT NULL
  AND gc.target_value IS NOT NULL
  AND gc.current_value <> gc.start_value
  AND gc.goal_category_status <> 'DELETED';
