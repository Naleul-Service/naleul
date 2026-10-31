package com.naleul.naleul.domain.admin.dto;

import com.naleul.naleul.domain.user.enums.AdminViewPlan;

/**
 * 운영진이 보고 있는 플랜.
 * @param plan    FREE | PRO
 * @param premium 지금 Pro 혜택이 걸리는지 (= plan == PRO) — 웹이 광고 · 배지 쿠키를 맞출 때 쓴다
 */
public record AdminViewPlanResponse(AdminViewPlan plan, boolean premium) {

    public static AdminViewPlanResponse of(AdminViewPlan plan) {
        return new AdminViewPlanResponse(plan, plan == AdminViewPlan.PRO);
    }
}
