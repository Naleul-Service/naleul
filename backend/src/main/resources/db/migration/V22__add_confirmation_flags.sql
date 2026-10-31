-- V{다음버전}__add_confirmation_flags.sql

ALTER TABLE mission_participants
    ADD COLUMN result_confirmed BOOLEAN NOT NULL DEFAULT TRUE; -- 기존 데이터는 이미 "확인된" 걸로 간주

ALTER TABLE mission_badges
    ADD COLUMN is_confirmed BOOLEAN NOT NULL DEFAULT TRUE; -- 기존 뱃지도 마찬가지