-- =====================================================================
-- V45: 22시 이월을 "제안"으로
--  못 한 Task 를 다음 날로 옮길 때 말없이 옮기면 "내가 잘못 넣었나?" 하고 헷갈린다.
--  carry_pending = TRUE 인 동안 화면은 점선 블록 + "어제 못한 ○○를 ○시로 옮길까요?" 를 보여주고,
--  사용자가 옮기기/안 옮기기를 고르면 FALSE. carried_from_date 는 원래 날짜 (문구용).
-- =====================================================================
ALTER TABLE task
    ADD COLUMN carry_pending     BOOLEAN NOT NULL DEFAULT FALSE COMMENT '이월 제안을 아직 확인 안 함',
    ADD COLUMN carried_from_date DATE    NULL COMMENT '이월되기 전 날짜';
