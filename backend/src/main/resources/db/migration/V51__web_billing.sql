-- =====================================================================
-- V51: 웹 Pro 구독 (토스페이먼츠 자동결제)
--  web_subscription : 사용자당 1행. 카드 등록(billing_key) · 이번 기간 · 다음 자동 결제 시각
--  web_payment      : 결제 1건 (성공 · 실패). order_id 유일 — 같은 결제를 두 번 기록하지 않게
--  Pro 여부는 기존처럼 users.is_premium · premium_expires_at 하나로 판단한다 (iOS 구독과 같은 칸).
-- =====================================================================
CREATE TABLE web_subscription (
    web_subscription_id  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id              BIGINT       NOT NULL,
    plan                 VARCHAR(20)  NOT NULL COMMENT 'PRO_MONTHLY · PRO_YEARLY',
    status               VARCHAR(20)  NOT NULL COMMENT 'PENDING · ACTIVE · PAST_DUE · CANCELED · EXPIRED',
    customer_key         VARCHAR(64)  NOT NULL COMMENT '토스 구매자 키 (UUID)',
    billing_key          VARCHAR(200) NULL     COMMENT '토스 자동결제 키 — 외부에 노출 금지',
    card_company         VARCHAR(30)  NULL,
    card_number          VARCHAR(30)  NULL     COMMENT '마스킹된 카드번호',
    current_period_start DATETIME(6)  NULL,
    current_period_end   DATETIME(6)  NULL,
    next_charge_at       DATETIME(6)  NULL     COMMENT '다음 자동 결제 시각 (기간 끝 하루 전 · 실패 시 하루 뒤)',
    period_no            INT          NOT NULL DEFAULT 0,
    failed_attempts      INT          NOT NULL DEFAULT 0,
    canceled_at          DATETIME(6)  NULL,
    test_mode            BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '토스 테스트 키로 만든 구독 (실제 돈 안 나감)',
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (web_subscription_id),
    UNIQUE KEY uk_ws_user (user_id),
    UNIQUE KEY uk_ws_customer_key (customer_key),
    KEY idx_ws_next_charge (status, next_charge_at),
    CONSTRAINT fk_ws_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

CREATE TABLE web_payment (
    web_payment_id      BIGINT       NOT NULL AUTO_INCREMENT,
    web_subscription_id BIGINT       NOT NULL,
    user_id             BIGINT       NOT NULL,
    order_id            VARCHAR(64)  NOT NULL,
    payment_key         VARCHAR(200) NULL     COMMENT '토스 결제 키 (환불할 때 필요)',
    plan                VARCHAR(20)  NOT NULL,
    amount              BIGINT       NOT NULL COMMENT '원 (부가세 포함)',
    status              VARCHAR(10)  NOT NULL COMMENT 'DONE · FAILED',
    failure_code        VARCHAR(60)  NULL,
    failure_message     VARCHAR(300) NULL,
    approved_at         DATETIME(6)  NULL,
    test_mode           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (web_payment_id),
    UNIQUE KEY uk_wp_order (order_id),
    KEY idx_wp_user (user_id, created_at),
    CONSTRAINT fk_wp_subscription FOREIGN KEY (web_subscription_id) REFERENCES web_subscription (web_subscription_id) ON DELETE CASCADE,
    CONSTRAINT fk_wp_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);
