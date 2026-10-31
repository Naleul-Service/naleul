-- =====================================================================
-- V64: AI 제안 품질 기록 (채택 · 수정 · 결과)
--  1. ai_suggestion_log    : AI 제안 1건 = 1행. 말로 고치기(GOAL_EDIT) · AI 일정 조정(SCHEDULE_EDIT) · 브레인 덤프(BRAIN_DUMP)
--                            미리보기(제안) 때 SUGGESTED 로 남기고, 적용(확정) 때 APPLIED 로 바꾼다.
--                            → 채택률 = APPLIED / 제안, 엉뚱한 제안 비율 = (suggested − valid) / suggested,
--                              일부만 적용한 비율 = (valid − applied) / valid, 브레인 덤프 수정률 = edited / applied
--                            사용자 입력 원문 · AI 응답은 저장하지 않는다. 브레인 덤프 비교용 지문은 제목 해시만, 확정 후 지운다.
--  2. ai_suggestion_target : 적용된 제안이 만든 · 옮긴 Task / 루틴 → "받아들인 뒤 실천했나(결과)" 집계용
-- =====================================================================
CREATE TABLE ai_suggestion_log (
    ai_suggestion_log_id BIGINT      NOT NULL AUTO_INCREMENT,
    user_id              BIGINT      NOT NULL,
    feature              VARCHAR(30) NOT NULL COMMENT 'GOAL_EDIT | SCHEDULE_EDIT | BRAIN_DUMP',
    target_goal_id       BIGINT      NULL     COMMENT '말로 고치기: 고친 목표',
    ai_used              BOOLEAN     NOT NULL DEFAULT TRUE COMMENT 'FALSE: AI 실패 · 한도로 규칙 해석 (품질 집계 제외)',
    suggested_count      INT         NOT NULL DEFAULT 0 COMMENT 'AI 가 낸 항목 · 변경 수',
    valid_count          INT         NOT NULL DEFAULT 0 COMMENT '서버 검증을 통과해 보여 준 수',
    status               VARCHAR(20) NOT NULL COMMENT 'SUGGESTED | APPLIED',
    applied_count        INT         NULL,
    skipped_count        INT         NULL     COMMENT '일정 조정: 잠긴 일정과 겹쳐 건너뜀',
    edited_count         INT         NULL     COMMENT '브레인 덤프: 확정 전에 고친 항목 수',
    goal_changed_count   INT         NULL     COMMENT '브레인 덤프: 연결 목표를 바꾼 항목 수',
    snapshot_json        TEXT        NULL     COMMENT '브레인 덤프 해석 지문 (제목 해시) — 확정 후 NULL',
    created_at           DATETIME(6) NOT NULL,
    applied_at           DATETIME(6) NULL,
    PRIMARY KEY (ai_suggestion_log_id),
    KEY idx_asl_user_feature_status (user_id, feature, status, created_at),
    KEY idx_asl_feature_created (feature, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ai_suggestion_target (
    ai_suggestion_target_id BIGINT      NOT NULL AUTO_INCREMENT,
    ai_suggestion_log_id    BIGINT      NOT NULL,
    target_type             VARCHAR(10) NOT NULL COMMENT 'TASK | ROUTINE',
    target_id               BIGINT      NOT NULL,
    PRIMARY KEY (ai_suggestion_target_id),
    KEY idx_ast_log (ai_suggestion_log_id),
    KEY idx_ast_type_target (target_type, target_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
