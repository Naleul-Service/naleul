-- V7__convert_etc_categories_to_normal.sql

UPDATE goal_category
SET is_default = false
WHERE is_default = true;

UPDATE general_category
SET is_default = false
WHERE is_default = true;