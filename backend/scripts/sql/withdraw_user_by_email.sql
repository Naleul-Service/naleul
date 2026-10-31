-- =====================================================================
-- 운영자가 특정 이메일의 계정을 직접 탈퇴 처리하는 스크립트 (MySQL 8)
--  - UserDataCleaner.java 와 같은 순서(자식 → 부모)로 삭제한다.
--  - 반드시 STEP 0(확인) → STEP 1~3 순서로 실행하고, 결과를 보고 COMMIT/ROLLBACK 을 고른다.
--  - 실행 전 백업 권장:
--      mysqldump -u <user> -p --single-transaction naleul > backup_$(date +%F_%H%M).sql
-- =====================================================================

-- ▼▼▼ 여기만 바꾸세요 ▼▼▼
SET @email := 'target@example.com';
-- ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲

-- 양쪽 collation 을 명시해서 'Illegal mix of collations(1267)' 에러를 피한다 (대소문자까지 정확히 일치해야 함)
SET @uid := (SELECT user_id FROM users WHERE user_email COLLATE utf8mb4_bin = @email COLLATE utf8mb4_bin);

-- STEP 0. 대상 확인 — 정확히 1건, 원하는 사람인지 확인 (NULL 이면 이메일이 틀린 것)
SELECT @uid AS user_id, user_email, user_name, apple_id
FROM users WHERE user_id = @uid;

-- 삭제될 양 미리 보기
SELECT 'task' t, COUNT(*) FROM task WHERE user_id = @uid
UNION ALL SELECT 'routine', COUNT(*) FROM routine WHERE user_id = @uid
UNION ALL SELECT 'goal_category', COUNT(*) FROM goal_category WHERE user_id = @uid
UNION ALL SELECT 'missions(hosted)', COUNT(*) FROM missions WHERE user_id = @uid
UNION ALL SELECT 'other users participating in hosted missions', COUNT(*)
          FROM mission_participants WHERE user_id <> @uid
          AND mission_id IN (SELECT mission_id FROM missions WHERE user_id = @uid);

-- STEP 1. 트랜잭션 시작 (중간에 에러가 나면 ROLLBACK 하면 아무것도 안 바뀜)
START TRANSACTION;

-- 탈퇴 기록 남기기 (앱에서 탈퇴한 것과 동일하게)
INSERT INTO user_withdrawal (user_id, user_email, reason, detail, withdrawn_at)
SELECT user_id, user_email, 'OTHER', '운영자 수동 탈퇴 처리', NOW()
FROM users WHERE user_id = @uid;

-- 1. 미션 인증 좋아요 / 인증
DELETE FROM mission_task_likes
WHERE user_id = @uid
   OR mission_task_complete_id IN (
        SELECT c.mission_task_complete_id FROM mission_task_completes c
        WHERE c.user_id = @uid
           OR c.task_id IN (
                SELECT t.task_id FROM task t
                WHERE t.user_id = @uid
                   OR t.mission_participant_id IN (
                        SELECT mp.mission_participant_id FROM mission_participants mp
                        WHERE mp.user_id = @uid
                           OR mp.mission_id IN (SELECT m.mission_id FROM missions m WHERE m.user_id = @uid))));

DELETE FROM mission_task_completes
WHERE user_id = @uid
   OR task_id IN (
        SELECT t.task_id FROM task t
        WHERE t.user_id = @uid
           OR t.mission_participant_id IN (
                SELECT mp.mission_participant_id FROM mission_participants mp
                WHERE mp.user_id = @uid
                   OR mp.mission_id IN (SELECT m.mission_id FROM missions m WHERE m.user_id = @uid)));

-- 2. 다른 유저의 Task 는 남기고, 사라질 미션 참여 기록과의 연결만 끊는다
UPDATE task SET mission_participant_id = NULL
WHERE user_id <> @uid
  AND mission_participant_id IN (
        SELECT mp.mission_participant_id FROM mission_participants mp
        WHERE mp.user_id = @uid
           OR mp.mission_id IN (SELECT m.mission_id FROM missions m WHERE m.user_id = @uid));

-- 3. 미션 부가 데이터
DELETE FROM mission_badges    WHERE user_id = @uid OR mission_id IN (SELECT m.mission_id FROM missions m WHERE m.user_id = @uid);
DELETE FROM mission_bookmarks WHERE user_id = @uid OR mission_id IN (SELECT m.mission_id FROM missions m WHERE m.user_id = @uid);
DELETE FROM mission_poke      WHERE sender_id = @uid OR target_user_id = @uid
                                 OR mission_id IN (SELECT m.mission_id FROM missions m WHERE m.user_id = @uid);

-- 4. 실제 수행 기록 → Task
DELETE FROM task_actual WHERE user_id = @uid OR task_id IN (SELECT t.task_id FROM task t WHERE t.user_id = @uid);
DELETE FROM task WHERE user_id = @uid;

-- 5. 미션 참여 기록 → 미션
DELETE FROM mission_participants WHERE user_id = @uid OR mission_id IN (SELECT m.mission_id FROM missions m WHERE m.user_id = @uid);
DELETE FROM mission_repeat_days  WHERE mission_id IN (SELECT m.mission_id FROM missions m WHERE m.user_id = @uid);
DELETE FROM missions WHERE user_id = @uid;

-- 5-1. 마일스톤 / AI 목표 만들기 대화 (develop: V33~)
DELETE FROM milestone WHERE goal_category_id IN (SELECT g.goal_category_id FROM goal_category g WHERE g.user_id = @uid);
DELETE FROM goal_creation_session WHERE user_id = @uid;

-- 6. 루틴
DELETE FROM routine_repeat_day WHERE routine_id IN (SELECT r.routine_id FROM routine r WHERE r.user_id = @uid);
DELETE FROM routine WHERE user_id = @uid;

-- 7. 카테고리를 참조하는 기록/리포트
DELETE FROM retrospective          WHERE user_id = @uid;
DELETE FROM goal_completion_report WHERE user_id = @uid;
DELETE FROM dream_board            WHERE user_id = @uid;
DELETE FROM idea_board             WHERE user_id = @uid;
DELETE FROM weekly_report          WHERE user_id = @uid;
DELETE FROM monthly_report         WHERE user_id = @uid;

-- 8. 자서전
DELETE FROM autobiography_answer
WHERE user_id = @uid
   OR question_id IN (SELECT q.question_id FROM autobiography_question q WHERE q.user_id = @uid);
DELETE FROM autobiography_question WHERE user_id = @uid;

-- 9. 카테고리 → 색상
DELETE FROM general_category WHERE user_id = @uid;
DELETE FROM goal_category    WHERE user_id = @uid;
DELETE FROM user_color       WHERE user_id = @uid;

-- 10. 알림 / 기기 / 로그인 토큰
DELETE FROM notifications         WHERE user_id = @uid;
DELETE FROM notification_settings WHERE user_id = @uid;
DELETE FROM device_tokens         WHERE user_id = @uid;
DELETE FROM `refresh-token`       WHERE user_id = @uid;
DELETE FROM refresh_session       WHERE user_id = @uid;
DELETE FROM ai_quota_usage        WHERE user_id = @uid;
-- purchase_transaction · iap_revenue_event 는 결제/매출 기록이라 보존 (FK 없음)
-- habit · life_pattern · daily_plan · goal_metric · work_topic · web_subscription 등은 ON DELETE CASCADE 로 함께 삭제됨

-- 11. 마지막으로 유저
DELETE FROM users WHERE user_id = @uid;

-- STEP 2. 확인 — 0 이어야 정상
SELECT COUNT(*) AS remaining_user FROM users WHERE user_id = @uid;

-- STEP 3. 문제 없으면 COMMIT, 이상하면 ROLLBACK
-- COMMIT;
-- ROLLBACK;
