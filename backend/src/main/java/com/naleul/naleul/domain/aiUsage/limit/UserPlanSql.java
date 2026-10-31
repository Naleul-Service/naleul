package com.naleul.naleul.domain.aiUsage.limit;

/**
 * "지금 Pro 인가"를 SQL 로 (User.hasPaidPremium 과 같은 규칙 — 운영진의 플랜 바꿔 보기는 보지 않는다).
 * users 테이블 별칭을 받아 'PRO' | 'FREE' 를 돌려주는 식.
 */
public final class UserPlanSql {

    private UserPlanSql() {
    }

    public static String planOf(String alias) {
        return "(CASE WHEN %1$s.is_premium = TRUE AND (%1$s.premium_expires_at IS NULL OR %1$s.premium_expires_at > NOW())"
                .formatted(alias) + " THEN 'PRO' ELSE 'FREE' END)";
    }
}
