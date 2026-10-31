-- =====================================================================
-- V62: Android Google Play 구독 (Play Billing)
--  앱이 결제하면 purchase_token 을 서버로 보내고, 서버가 Google Play Developer API 로 확인 · 확인 처리(acknowledge)한 뒤
--  users.is_premium · premium_expires_at 을 늘린다 (웹 · iOS 와 같은 칸).
--  갱신 · 해지 · 환불은 Play 실시간 개발자 알림(RTDN, Pub/Sub push)으로 받아 같은 토큰을 다시 조회해 맞춘다.
--  purchase_token 은 구독 하나(갱신해도 그대로)의 키. 요금제를 바꾸면 새 토큰이 생기고 linked_purchase_token 이 이전 토큰.
-- =====================================================================
CREATE TABLE play_subscription (
    play_subscription_id  BIGINT        NOT NULL AUTO_INCREMENT,
    user_id               BIGINT        NOT NULL,
    purchase_token        VARCHAR(512)  NOT NULL COMMENT 'Play 구매 토큰 — 외부에 노출 금지',
    product_id            VARCHAR(100)  NOT NULL COMMENT 'com.naleul.pro.monthly · com.naleul.pro.yearly',
    base_plan_id          VARCHAR(100)  NULL,
    latest_order_id       VARCHAR(100)  NULL,
    state                 VARCHAR(40)   NOT NULL COMMENT 'Play subscriptionState (SUBSCRIPTION_STATE_ACTIVE …)',
    expiry_time           DATETIME(6)   NULL,
    auto_renewing         BOOLEAN       NOT NULL DEFAULT FALSE,
    acknowledged          BOOLEAN       NOT NULL DEFAULT FALSE,
    test_purchase         BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '라이선스 테스터 결제 (실제 돈 안 나감)',
    linked_purchase_token VARCHAR(512)  NULL COMMENT '요금제 변경 전 토큰',
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    PRIMARY KEY (play_subscription_id),
    UNIQUE KEY uk_ps_token (purchase_token),
    KEY idx_ps_user (user_id, expiry_time),
    CONSTRAINT fk_ps_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);
