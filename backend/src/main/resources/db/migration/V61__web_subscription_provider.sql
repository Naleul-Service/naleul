-- =====================================================================
-- V61: 웹 Pro 구독 결제사 — 카드 정기구독(토스페이먼츠 빌링)에 토스페이 정기결제를 더한다.
--  provider : TOSS_PAYMENTS_CARD(카드 등록 → 토스페이먼츠 자동결제) · TOSS_PAY(토스 앱 인증 → 토스페이 자동결제)
--  둘 다 매달 · 매년 자동결제이고, 해지하면 이번 기간 끝까지 Pro (같은 web_subscription 행 · 같은 스케줄러).
--  토스페이는 billing_key 에 토스페이 빌링키, card_company / card_number 에 토스 앱에서 고른 결제수단을 적는다.
--  billing_key 인덱스: 토스페이가 사용자 해지(REMOVED)를 콜백으로 알려줄 때 빌링키로 구독을 찾는다.
-- =====================================================================
ALTER TABLE web_subscription
    ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'TOSS_PAYMENTS_CARD'
        COMMENT 'TOSS_PAYMENTS_CARD(카드 정기구독) · TOSS_PAY(토스페이 정기구독)' AFTER status,
    ADD KEY idx_ws_billing_key (billing_key);
