ALTER TABLE mission_badges ADD COLUMN category_item VARCHAR(20) NULL;

-- 기존 CATEGORY_PUZZLE(대분류 기준) 뱃지는 세분화된 항목 정보를 역산할 수 없으므로
-- badge_type 이름만 새로 이어받고 category_item은 비워둠
-- (이 레코드들은 앞으로 "항목별 조회/레벨업" 로직에는 매칭되지 않지만, 획득 이력 자체는 보존됨)
UPDATE mission_badges SET badge_type = 'ITEM_PUZZLE' WHERE badge_type = 'CATEGORY_PUZZLE';

ALTER TABLE mission_badges DROP COLUMN category;