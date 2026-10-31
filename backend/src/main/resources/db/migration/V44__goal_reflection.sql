-- =====================================================================
-- V44: 목표 하루 회고 (목표 상세 "오늘 기록" 카드)
--  수치(몸무게 등) 옆에 "오늘 무엇을 했는지 · 어땠는지"를 남긴다. 예) "점심에 마라탕 먹음" · 아쉬워요
--  수치가 없는 목표에도, 수치 없이 글만으로도 남길 수 있어서 goal_metric_log 와 따로 둔다.
--  목표 · 날짜당 1행 (같은 날 다시 쓰면 덮어씀). "말로 고치기" AI 가 최근 회고를 근거로 계획을 조정한다.
-- =====================================================================
CREATE TABLE goal_reflection (
    goal_reflection_id BIGINT       NOT NULL AUTO_INCREMENT,
    goal_category_id   BIGINT       NOT NULL,
    user_id            BIGINT       NOT NULL,
    reflect_date       DATE         NOT NULL COMMENT '한국 날짜',
    mood               VARCHAR(10)  NULL COMMENT 'GOOD 잘했어요 · OK 그럭저럭 · BAD 아쉬워요',
    note               VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '무엇을 했는지 · 느낀 점',
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (goal_reflection_id),
    UNIQUE KEY uk_gr_goal_date (goal_category_id, reflect_date),
    KEY idx_gr_user (user_id),
    CONSTRAINT fk_gr_goal_category FOREIGN KEY (goal_category_id) REFERENCES goal_category (goal_category_id) ON DELETE CASCADE,
    CONSTRAINT fk_gr_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);
