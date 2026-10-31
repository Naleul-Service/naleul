package com.naleul.naleul.domain.webBilling.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 웹 구독 상품 — iOS 앱의 Pro 월간(com.naleul.pro.monthly) · 연간(com.naleul.pro.yearly)과 같은 혜택 */
@Getter
@RequiredArgsConstructor
public enum WebPlan {
    PRO_MONTHLY(1, "나를 Pro 월간"),
    PRO_YEARLY(12, "나를 Pro 연간");

    /** 한 번 결제로 이용하는 개월 수 */
    private final int months;
    /** 토스 결제창 · 카드 명세서에 보이는 이름 */
    private final String orderName;
}
