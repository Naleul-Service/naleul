-- 이월 개선 (2026-10-27)
-- 못 한 일을 다음 날로 자동으로 옮길지. 끄면 그날에 '놓침'으로 남고 사용자가 직접 옮긴다.
-- 켜져 있으면 저녁이 아니라 다음 날 아침 기상 시간에 옮긴다 (새벽까지 하는 일이 사라지지 않게).
ALTER TABLE planner_setting
    ADD COLUMN auto_carry_enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '못 한 일을 다음 날 아침 자동 이월';
