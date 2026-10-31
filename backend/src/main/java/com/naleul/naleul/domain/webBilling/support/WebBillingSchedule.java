package com.naleul.naleul.domain.webBilling.support;

import com.naleul.naleul.domain.webBilling.enums.WebPlan;

import java.time.LocalDateTime;

/**
 * 결제 일정 계산 (DB 없이 순수 계산).
 *
 *  - 기간: 결제한 시각부터 1개월(연간 12개월). 갱신하면 이전 기간 끝에서 이어 붙여요 (결제 시각이 아니라).
 *  - 자동 결제: 기간이 끝나기 하루 전. 실패하면 하루 뒤 다시, 최대 MAX_ATTEMPTS 번.
 *  - Pro 이용: 기간 끝까지. 다시 시도하는 동안 기간이 지나면 자정 만료 배치가 Pro 를 끄고,
 *    나중에 결제가 성공하면 다시 켜요.
 */
public final class WebBillingSchedule {

    public static final int MAX_ATTEMPTS = 3;
    static final int CHARGE_DAYS_BEFORE_END = 1;

    private WebBillingSchedule() {
    }

    public static LocalDateTime periodEnd(LocalDateTime periodStart, WebPlan plan) {
        return periodStart.plusMonths(plan.getMonths());
    }

    /** 다음 자동 결제 시각 = 기간 끝 하루 전 (이미 지났으면 지금) */
    public static LocalDateTime firstChargeAt(LocalDateTime periodEnd, LocalDateTime now) {
        LocalDateTime at = periodEnd.minusDays(CHARGE_DAYS_BEFORE_END);
        return at.isBefore(now) ? now : at;
    }

    /** 실패 후 다음 시도 시각. 더 시도하지 않으면 null */
    public static LocalDateTime retryAt(int failedAttempts, LocalDateTime now) {
        return failedAttempts >= MAX_ATTEMPTS ? null : now.plusDays(1);
    }

    /** 주문번호 — 같은 기간 · 같은 시도면 같은 번호라 두 서버가 동시에 돌아도 토스가 한 번만 결제해요 (멱등키) */
    public static String orderId(long subscriptionId, int periodNo, int attempt) {
        return "naleul-" + subscriptionId + "-" + periodNo + "-" + attempt;
    }
}
