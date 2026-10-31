-- =====================================================================
-- V43: 고정 시간(기본 패턴) 요일별 시간 + 시작 날짜 차이
--  1) life_pattern_day_time : 요일마다 다른 시간 (예: 주말 수면 01:00~09:30).
--     없는 요일은 life_pattern.start_time / end_time (기본 시간)을 따른다.
--  2) start_day_offset : 블록이 소속 날짜(target_date / 그 요일)보다 하루 앞(-1)·뒤(+1)에 시작할 때.
--     수면 00:00~07:00 의 토요일 블록을 "금요일 밤 23:30 취침"으로 바꾸면 시작이 금요일로 넘어가므로
--     (토요일, 23:30, -1) 로 저장한다. 소속 날짜를 그대로 둬서 그날만 변경·요일 판정이 꼬이지 않게.
-- =====================================================================
CREATE TABLE life_pattern_day_time (
    life_pattern_id  BIGINT      NOT NULL,
    day_of_week      VARCHAR(10) NOT NULL COMMENT 'MONDAY ~ SUNDAY',
    start_time       TIME        NOT NULL,
    end_time         TIME        NOT NULL,
    start_day_offset INT         NOT NULL DEFAULT 0 COMMENT '-1 전날 시작 · 0 그날 · +1 다음 날 시작',
    PRIMARY KEY (life_pattern_id, day_of_week),
    CONSTRAINT fk_life_pattern_day_time_pattern FOREIGN KEY (life_pattern_id)
        REFERENCES life_pattern (life_pattern_id) ON DELETE CASCADE
);

ALTER TABLE life_pattern_override
    ADD COLUMN start_day_offset INT     NOT NULL DEFAULT 0
        COMMENT '-1 전날 시작 · 0 그날 · +1 다음 날 시작 (기상·취침 드래그)' AFTER end_time;
