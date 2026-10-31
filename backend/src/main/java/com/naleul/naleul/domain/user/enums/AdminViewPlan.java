package com.naleul.naleul.domain.user.enums;

/**
 * 운영진(ADMIN)이 지금 어떤 플랜으로 앱을 보고 있는지 (2026-10-24, V66).
 * 운영진은 결제와 상관없이 Free · Pro 화면을 바꿔 가며 확인할 수 있다. 기본은 PRO.
 * User.isCurrentlyPremium() 이 운영진이면 이 값을 따르므로, 하루 횟수 제한 · Pro 전용 기능도 그대로 바뀐다.
 */
public enum AdminViewPlan {
    FREE, PRO
}
