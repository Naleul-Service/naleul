-- V{다음번호}__add_actual_end_date.sql

ALTER TABLE general_category
    ADD COLUMN actual_end_date DATE NULL;

ALTER TABLE routine
    ADD COLUMN actual_end_date DATE NULL;