ALTER TABLE mission_participants
    ADD COLUMN application_result_notified BOOLEAN NOT NULL DEFAULT FALSE;

-- 기존에 이미 승인/거절된 데이터는 예전 방식(즉시발송)으로 이미 알림이 나갔으므로
-- 마이그레이션 시점에 배치 대상에서 제외되도록 true로 채움
UPDATE mission_participants
SET application_result_notified = TRUE
WHERE status IN ('ACTIVE', 'REJECTED');