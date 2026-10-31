-- =====================================================================
-- V41: 루틴 요일별 시간
--  같은 루틴이라도 요일마다 시간을 다르게 할 수 있다 (예: 월·금 20:00, 수 07:00).
--  routine.repeat_start_time / repeat_end_time 이 기본 시간이고,
--  여기 값이 있는 요일만 그 시간으로 덮어쓴다. 둘 다 NULL 이면 기본 시간을 따른다.
-- =====================================================================
ALTER TABLE routine_repeat_day
    ADD COLUMN start_time TIME NULL COMMENT '이 요일만 다른 시작 시각 (NULL = 루틴 기본 시간)',
    ADD COLUMN end_time   TIME NULL COMMENT '이 요일만 다른 종료 시각 (NULL = 루틴 기본 시간)';
