-- 운영진(ADMIN)이 Free / Pro 중 어떤 플랜으로 앱을 볼지 (2026-10-24)
-- NULL = 기본(PRO). 운영진이 아니면 쓰지 않는다.
ALTER TABLE users
    ADD COLUMN admin_view_plan VARCHAR(10) NULL COMMENT 'ADMIN 전용: FREE | PRO (NULL = PRO)';
