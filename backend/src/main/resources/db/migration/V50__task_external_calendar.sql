-- =====================================================================
-- V50: 외부 캘린더(구글 · 아이폰 기본 캘린더) 일정 가져오기
--  task.external_source   : GOOGLE (웹에서 구글 연결) · APPLE (iOS 앱이 아이폰 캘린더에서). 직접 만든 Task 는 NULL
--  task.external_event_id : 외부 캘린더의 일정 id (반복 일정은 회차마다 다름)
--  같은 일정을 두 번 가져오지 않도록 (user_id, external_source, external_event_id) 유니크.
--  MySQL 유니크 인덱스는 NULL 을 서로 다른 값으로 보므로 기존 Task(NULL)에는 영향 없음.
--  task.source 에 EXTERNAL_CALENDAR 값이 새로 들어간다 (VARCHAR(20) 이라 컬럼 변경 없음)
-- =====================================================================
ALTER TABLE task
    ADD COLUMN external_source   VARCHAR(10)  NULL COMMENT 'GOOGLE · APPLE (외부 캘린더에서 가져온 Task)',
    ADD COLUMN external_event_id VARCHAR(255) NULL COMMENT '외부 캘린더 일정 id';

CREATE UNIQUE INDEX uk_task_external_event ON task (user_id, external_source, external_event_id);
