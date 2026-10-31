package com.naleul.naleul.domain.purchase.enums;

public enum SubscriptionStatus {
    ACTIVE,          // 활성 상태 (정상 이용 가능)
    EXPIRED,         // 만료됨
    REVOKED,         // 환불/취소로 인해 강제 회수됨
    GRACE_PERIOD,    // 결제 실패했지만 유예기간 (계속 이용 가능)
    BILLING_RETRY,   // 결제 재시도 중 (Apple이 자동으로 재시도하는 기간)
    IN_TRIAL         // 무료 체험 중
}