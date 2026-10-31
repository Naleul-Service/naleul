-- =====================================================================
-- V49: 목표 결과 측정 보강 (목표 마무리 버튼을 누르지 않아도 결과를 믿을 수 있게)
--  1. metric_unrecorded : 수치 목표인데 수치를 기록하지 않아 실천률로 판정했다.
--                         지금까지는 기록이 없으면 현재값 = 시작값 → 진행률 0% → "미달성"이 되어
--                         "기록 안 함"과 "실패"가 섞였다.
--  2. auto_result       : 서버의 자동 판정. 사용자가 결과를 직접 골라도 남겨 둬서
--                         result 와 비교하면 "자동 판정 기준(80% · 50%)이 맞는지"(판정 일치율)를 알 수 있다.
--  3. longest_idle_days : 기간 중 아무 활동(Task 완료 · 수치 기록)도 없던 가장 긴 구간(일).
--                         14일 이상이면 중간에 멈췄던 목표 → 완주율 계산용.
-- =====================================================================
ALTER TABLE goal_outcome
    ADD COLUMN metric_unrecorded BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '수치 목표인데 수치 기록이 없어 실천률로 판정',
    ADD COLUMN auto_result       VARCHAR(20) NULL COMMENT '자동 판정 ACHIEVED · PARTIAL · NOT_ACHIEVED (V49 이전 USER 결과는 NULL)',
    ADD COLUMN longest_idle_days INT         NULL COMMENT '활동 없던 가장 긴 구간(일), V49 이전 결과는 NULL';

-- 기존 자동 판정 결과는 그 자체가 자동 판정
UPDATE goal_outcome SET auto_result = result WHERE source = 'AUTO';

-- 기존 결과 중 수치 기록이 한 번도 없던 수치 목표 → 수치 미기록으로 표시하고 진행률은 지운다
UPDATE goal_outcome o
SET o.metric_unrecorded = TRUE,
    o.metric_percent    = NULL
WHERE o.metric_percent IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM goal_metric_log l WHERE l.goal_category_id = o.goal_category_id);

-- 그중 자동 판정이었던 결과는 실천률로 다시 판정 (GoalOutcomeCalculator.infer 와 같은 기준)
UPDATE goal_outcome
SET result = CASE
        WHEN COALESCE(routine_rate, task_rate) >= 80 THEN 'ACHIEVED'
        WHEN COALESCE(routine_rate, task_rate) >= 50 THEN 'PARTIAL'
        ELSE 'NOT_ACHIEVED'
    END
WHERE metric_unrecorded = TRUE AND source = 'AUTO';

UPDATE goal_outcome SET auto_result = result WHERE metric_unrecorded = TRUE AND source = 'AUTO';
