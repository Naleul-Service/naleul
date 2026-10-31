-- =====================================================================
-- V48: 운영 대시보드용 기록 테이블
--  1. ai_call_log       : Claude API 호출 1번 = 1행. 모델 · 토큰 수 · 성공 여부.
--                         지금까지는 토큰이 로그 파일에만 남아서(goal_draft 제외) AI 비용을 계산할 수 없었다.
--                         사용자 입력 원문은 저장하지 않는다 (명세 8-1 로그 규칙과 같음).
--  2. iap_revenue_event : 결제 1건 = 1행. 실제 결제 금액(Apple 이 알려주는 price · currency).
--                         purchase_transaction 은 갱신될 때마다 같은 행을 덮어써서(renewFrom)
--                         "이번 달 몇 번 결제됐고 얼마였는지" 를 알 수 없었다.
-- =====================================================================
CREATE TABLE ai_call_log (
    ai_call_log_id              BIGINT       NOT NULL AUTO_INCREMENT,
    feature                     VARCHAR(30)  NOT NULL COMMENT 'GOAL_INTERVIEW · GOAL_PLAN · GOAL_PLAN_REVISION · GOAL_EDIT · BRAIN_DUMP · PATTERN_POLISH · REPORT_FEEDBACK',
    model                       VARCHAR(80)  NULL     COMMENT '응답에 담긴 실제 모델 id',
    input_tokens                INT          NOT NULL DEFAULT 0,
    output_tokens               INT          NOT NULL DEFAULT 0,
    cache_creation_input_tokens INT          NOT NULL DEFAULT 0,
    cache_read_input_tokens     INT          NOT NULL DEFAULT 0,
    latency_ms                  BIGINT       NOT NULL DEFAULT 0,
    success                     BOOLEAN      NOT NULL DEFAULT TRUE COMMENT 'FALSE: API 오류 · 타임아웃 (토큰 0, 과금 없음)',
    created_at                  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (ai_call_log_id),
    KEY idx_acl_created (created_at),
    KEY idx_acl_feature_created (feature, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE iap_revenue_event (
    iap_revenue_event_id    BIGINT        NOT NULL AUTO_INCREMENT,
    user_id                 BIGINT        NULL,
    transaction_id          VARCHAR(100)  NOT NULL,
    original_transaction_id VARCHAR(100)  NOT NULL,
    product_id              VARCHAR(150)  NOT NULL,
    event_type              VARCHAR(20)   NOT NULL COMMENT 'PURCHASE 첫 결제 · RENEWAL 자동 갱신 · REFUND 환불',
    price_amount            DECIMAL(12,2) NULL     COMMENT 'Apple price(밀리 단위)/1000. 무료 체험이면 0, 모르면 NULL',
    currency                VARCHAR(3)    NULL     COMMENT 'ISO 4217 (KRW, USD …)',
    environment             VARCHAR(20)   NOT NULL COMMENT 'Production · Sandbox',
    event_at                DATETIME(6)   NOT NULL COMMENT '결제(또는 환불) 시각',
    created_at              DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (iap_revenue_event_id),
    UNIQUE KEY uk_ire_tx_type (transaction_id, event_type),
    KEY idx_ire_event_at (event_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
