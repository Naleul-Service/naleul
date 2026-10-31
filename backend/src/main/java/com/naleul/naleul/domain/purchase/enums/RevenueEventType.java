package com.naleul.naleul.domain.purchase.enums;

/** iap_revenue_event 종류 */
public enum RevenueEventType {
    PURCHASE,   // 첫 결제 (무료 체험 시작이면 금액 0)
    RENEWAL,    // 자동 갱신 결제
    REFUND      // 환불 (금액은 환불된 결제의 금액)
}
