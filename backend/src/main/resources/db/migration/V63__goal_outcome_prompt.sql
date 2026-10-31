-- =====================================================================
-- V63: 목표 결과 확인 모달 + 자동 판정 근거
--  1. auto_reason          : 자동 판정에 쓴 근거 (METRIC · ROUTINE_RATE · TASK_RATE · NO_RECORD)
--                            → "마지막 기록이 목표 수치의 86%예요. 80% 이상이라 자동으로 달성 처리했어요." 문구와
--                              대시보드 "판정 근거별 일치율" 에 쓴다.
--  2. prompt_status        : 기한이 지나 자동 판정된 목표를 앱을 열 때 "목표는 만족했나요?" 로 다시 묻는 상태
--                            PENDING 물어볼 것 · ANSWERED 직접 고름 · DISMISSED 나중에(자동 판정 유지). 직접 마무리한 목표는 NULL
--  3. prompt_responded_at  : 답하거나 미룬 시각 (서버 시간대, created_at 과 같은 기준)
--  자동 판정 기준도 함께 바뀐다: 수치 목표 100% → 80% 이상이면 달성 (GoalOutcomeCalculator.METRIC_ACHIEVED_PERCENT)
-- =====================================================================
ALTER TABLE goal_outcome
    ADD COLUMN auto_reason         VARCHAR(20) NULL COMMENT 'METRIC | ROUTINE_RATE | TASK_RATE | NO_RECORD',
    ADD COLUMN prompt_status       VARCHAR(20) NULL COMMENT 'PENDING | ANSWERED | DISMISSED (자동 판정된 목표만)',
    ADD COLUMN prompt_responded_at DATETIME(6) NULL;

CREATE INDEX idx_go_user_prompt ON goal_outcome (user_id, prompt_status, created_at);

-- 기존 결과의 판정 근거 (GoalOutcomeCalculator.reasonOf 와 같은 우선순위)
UPDATE goal_outcome
SET auto_reason = CASE
        WHEN metric_percent IS NOT NULL THEN 'METRIC'
        WHEN routine_rate   IS NOT NULL THEN 'ROUTINE_RATE'
        WHEN task_rate      IS NOT NULL THEN 'TASK_RATE'
        ELSE 'NO_RECORD'
    END;

-- 수치 목표 달성 기준 80% 로 다시 판정 (자동 판정만. 사용자가 고른 result 는 그대로)
UPDATE goal_outcome
SET auto_result = 'ACHIEVED'
WHERE metric_percent >= 80 AND metric_percent < 100 AND auto_result = 'PARTIAL';

UPDATE goal_outcome
SET result = 'ACHIEVED'
WHERE source = 'AUTO' AND metric_percent >= 80 AND metric_percent < 100 AND result = 'PARTIAL';

-- 최근 14일 안에 자동 판정된 목표는 바로 물어본다 (기능을 켜자마자 일치율 데이터가 쌓이도록)
UPDATE goal_outcome
SET prompt_status = 'PENDING'
WHERE source = 'AUTO' AND created_at >= DATE_SUB(NOW(), INTERVAL 14 DAY);
