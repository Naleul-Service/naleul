-- =====================================================================
-- V39: 목표 형태 (goal_mode)
--  ACHIEVEMENT : 달성형 — 수치·기간·세부 목표·마일스톤이 있는 기존 목표 (AI 설계 포함)
--  RECORD      : 기록형 — 회사 업무·사이드 프로젝트처럼 "이루는 것"보다 "한 일을 쌓아 두는" 목표.
--                종료일·수치·마일스톤 없이 이름만으로 만들고, 실제로 한 일(actual_activity)을 모아 본다.
--  기존 목표는 모두 ACHIEVEMENT.
-- =====================================================================
ALTER TABLE goal_category
    ADD COLUMN goal_mode VARCHAR(20) NOT NULL DEFAULT 'ACHIEVEMENT' COMMENT 'ACHIEVEMENT(달성형) / RECORD(기록형)';

-- 기록형 목표 상세의 "한 일" 타임라인 조회용
CREATE INDEX idx_aa_goal_start ON actual_activity (goal_category_id, start_at);
