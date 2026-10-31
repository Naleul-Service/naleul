package com.naleul.naleul.domain.webBilling.enums;

/** 웹 구독을 결제하는 곳 — 둘 다 매달 · 매년 자동결제, 해지하면 기간 끝까지 Pro */
public enum WebPayProvider {
    /** 토스페이먼츠 결제창에서 카드 등록 → 토스페이먼츠 자동결제(빌링) */
    TOSS_PAYMENTS_CARD,
    /** 토스 앱에서 인증 → 토스페이 자동결제 (카드 · 토스머니) */
    TOSS_PAY
}
