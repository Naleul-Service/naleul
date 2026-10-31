-- ============================================================
-- V56: 기본 생활 습관에 시간대 (선택)
--
-- 예) 샤워 20:00 ~ 20:30 → 캘린더(TimeTable)에 그 시간 블록으로 보이고, 자동 배치가 그 시간을 비워 둔다.
-- 시간대를 안 정한 습관은 지금처럼 체크리스트에만 보인다. 둘 다 있거나 둘 다 없어야 한다.
-- ============================================================

ALTER TABLE habit
    ADD COLUMN start_time TIME NULL COMMENT '하는 시간 시작 (선택)' AFTER remind_time,
    ADD COLUMN end_time   TIME NULL COMMENT '하는 시간 끝 (선택, start_time 보다 뒤)' AFTER start_time;
