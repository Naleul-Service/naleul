-- =====================================================================
-- V53: 업무형 마감을 "목표 전체"에서 "주제마다"로
--  - 업무형(RECORD) 목표는 전체 마감이 없다 → 상세 화면은 주제를 마감 순서대로 잇는 세로 로드맵으로 보여준다
--    예) 취업 준비: 포트폴리오(10/20) → 이력서 제출(10/31) → 코딩 테스트(11/8) → 면접
--  - work_topic.due_date    : 주제 마감 (선택)
--  - work_topic.completed_at: 사용자가 "끝냈어요"로 표시한 시각 (Task 가 없는 주제도 끝낼 수 있게)
--  - V52 에서 잠깐 받던 업무형 목표 전체 마감(goal_category_end_date)은 비운다
--    (업무형은 자동 만료 대상이 아니라서 값이 남아 있어도 동작은 같지만, 화면 · 통계에서 헷갈리지 않게)
-- =====================================================================
ALTER TABLE work_topic
    ADD COLUMN due_date     DATE        NULL COMMENT 'V53: 주제 마감 (선택)',
    ADD COLUMN completed_at DATETIME(6) NULL COMMENT 'V53: 직접 끝냈다고 표시한 시각';

UPDATE goal_category
SET goal_category_end_date = NULL
WHERE goal_mode = 'RECORD'
  AND goal_category_end_date IS NOT NULL;
