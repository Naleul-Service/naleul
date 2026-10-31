package com.naleul.naleul.domain.webBilling.enums;

/**
 * 웹 구독 상태
 *  - PENDING  : 카드 등록 창을 열었지만 아직 첫 결제 전
 *  - ACTIVE   : 이용 중 · 기간이 끝나기 하루 전에 자동 결제
 *  - PAST_DUE : 자동 결제 실패 → 하루 간격으로 다시 시도 (최대 3번)
 *  - CANCELED : 해지 신청 — 이번 기간 끝까지 쓰고, 더 결제하지 않음
 *  - EXPIRED  : 끝남
 */
public enum WebSubscriptionStatus {
    PENDING,
    ACTIVE,
    PAST_DUE,
    CANCELED,
    EXPIRED
}
