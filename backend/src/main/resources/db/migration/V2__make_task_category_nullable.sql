-- ============================================================
-- V2__make_task_category_nullable.sql
--
-- 미션(Mission)으로부터 생성되는 Task는 GoalCategory, GeneralCategory가
-- 없을 수 있음 (Task.fromMission() 참고). 기존에는 일반 Task만 존재한다는
-- 전제로 두 컬럼이 NOT NULL 이었으나, 이제는 nullable 로 변경해야 함.
--
-- FK 제약은 그대로 유지됨 (NULL 값은 FK 검사 대상에서 제외되므로 문제 없음).
-- ============================================================

ALTER TABLE `task`
    MODIFY COLUMN `general_category_id` bigint NULL,
    MODIFY COLUMN `goal_category_id` bigint NULL;
