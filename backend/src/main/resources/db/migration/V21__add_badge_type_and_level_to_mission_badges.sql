-- V{다음 버전번호}__add_badge_type_and_level_to_mission_badges.sql

ALTER TABLE mission_badges
    ADD COLUMN badge_type VARCHAR(30) NOT NULL DEFAULT 'CATEGORY_PUZZLE',
    ADD COLUMN category VARCHAR(20) NULL,
    ADD COLUMN level INT NOT NULL DEFAULT 1;

-- 기존 데이터 백필: 이전 로직(awardBadgeIfEligible)으로 지급된 뱃지는 전부 미션 성공 기준이었으므로
-- CATEGORY_PUZZLE로 간주하고, 각 미션의 카테고리를 category 컬럼에 채워넣음
UPDATE mission_badges mb
    JOIN missions m ON m.mission_id = mb.mission_id
    SET mb.category = m.category
WHERE mb.badge_type = 'CATEGORY_PUZZLE';