-- =====================================================================
-- V37: 목표 카테고리 2단계 (명세 6.A.1.2) + 루틴 방법 설명
--  1. goal_category          : goal_type / goal_sub_type / goal_kind_label (기타일 때 직접 적은 이름, 10자)
--  2. goal_creation_session  : 시작 화면에서 고른 카테고리 (계획 AI 가 카테고리별 가이드를 고르는 기준)
--  3. routine.description    : AI 루틴의 구체적인 방법 ("스쿼트 4x10, 런지 3x12 …")
-- =====================================================================

ALTER TABLE goal_category
    ADD COLUMN goal_type       VARCHAR(20) NULL COMMENT 'HEALTH | LEARNING | HABIT | ETC',
    ADD COLUMN goal_sub_type   VARCHAR(30) NULL COMMENT 'EXERCISE, DIET, LANGUAGE, WAKE_UP … (GoalSubType)',
    ADD COLUMN goal_kind_label VARCHAR(10) NULL COMMENT '기타 카테고리일 때 사용자가 적은 이름';

ALTER TABLE goal_creation_session
    ADD COLUMN goal_type       VARCHAR(20) NULL,
    ADD COLUMN goal_sub_type   VARCHAR(30) NULL,
    ADD COLUMN goal_kind_label VARCHAR(10) NULL;

ALTER TABLE routine
    ADD COLUMN description VARCHAR(300) NULL COMMENT '루틴 방법 (AI 목표 루틴)';

-- 기존 목표: 1단계만 기존 주제(goal_topic)에서 옮긴다. 2단계는 알 수 없으므로 비워 둔다.
UPDATE goal_category
SET goal_type = CASE goal_topic
                    WHEN 'HEALTH' THEN 'HEALTH'
                    WHEN 'STUDY' THEN 'LEARNING'
                    WHEN 'CAREER' THEN 'LEARNING'
                    WHEN 'LIFE' THEN 'HABIT'
                    ELSE NULL
                END
WHERE goal_type IS NULL;
