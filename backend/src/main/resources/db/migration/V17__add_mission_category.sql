-- V17__add_mission_category.sql

-- 1) 신규 컬럼 추가
ALTER TABLE missions ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'ETC';
ALTER TABLE missions ADD COLUMN category_item VARCHAR(20) NULL;
ALTER TABLE missions ADD COLUMN custom_category_name VARCHAR(10) NULL;
ALTER TABLE missions ADD COLUMN custom_item_name VARCHAR(10) NULL;

-- 2) 기존 NOT NULL 컬럼을 선택 항목으로 완화
ALTER TABLE missions MODIFY success_condition VARCHAR(500) NULL;
ALTER TABLE missions MODIFY fail_condition VARCHAR(500) NULL;

-- 3) repeat_day_type: NULL인 기존 row를 먼저 채운 뒤 NOT NULL 제약
UPDATE missions SET repeat_day_type = 'EVERYDAY' WHERE repeat_day_type IS NULL;
ALTER TABLE missions MODIFY repeat_day_type VARCHAR(20) NOT NULL;

-- 4) 제한 인원 범위 변경(1~4명)에 따른 기존 데이터 점검
--    기존에 5명으로 저장된 row가 있으면 애플리케이션 레벨 검증(@Max(4))과 어긋나므로 확인 필요
-- SELECT * FROM missions WHERE max_participants > 4;