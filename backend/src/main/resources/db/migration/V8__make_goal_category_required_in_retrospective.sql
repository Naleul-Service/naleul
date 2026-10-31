-- V8__make_goal_category_required_in_retrospective.sql

-- 2단계: 컬럼을 NOT NULL로 변경
ALTER TABLE retrospective
    MODIFY COLUMN goal_category_id BIGINT NOT NULL;