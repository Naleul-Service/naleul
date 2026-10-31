package com.naleul.naleul.domain.admin.service;

import com.naleul.naleul.domain.admin.dto.AdminViewPlanResponse;
import com.naleul.naleul.domain.admin.support.AdminGuard;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.enums.AdminViewPlan;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영진의 "플랜 바꿔 보기" (2026-10-24).
 * 결제 기록은 건드리지 않고 User.adminViewPlan 만 바꾼다 → isCurrentlyPremium() 이 이 값을 따르므로
 * 하루 횟수 제한 · Pro 전용 기능 잠금이 실제 Free / Pro 사용자와 똑같이 걸린다.
 */
@Service
@RequiredArgsConstructor
public class AdminViewPlanService {

    private final AdminGuard adminGuard;
    private final UserFinder userFinder;

    @Transactional(readOnly = true)
    public AdminViewPlanResponse get(Long userId) {
        adminGuard.check(userId);
        User user = ensureAdmin(userFinder.findUser(userId));
        return AdminViewPlanResponse.of(user.adminViewPlanOrNull());
    }

    @Transactional
    public AdminViewPlanResponse change(Long userId, AdminViewPlan plan) {
        adminGuard.check(userId);
        User user = ensureAdmin(userFinder.findUser(userId));
        user.changeAdminViewPlan(plan);
        return AdminViewPlanResponse.of(user.adminViewPlanOrNull());
    }

    /**
     * 플랜 바꿔 보기는 user_role = ADMIN 인 사용자만 (id 목록으로만 운영진이면 대시보드만 볼 수 있다).
     * 결제 없이 Pro 혜택을 받는 권한이라 자동으로 올리지 않는다 — DB 에서 user_role 을 ADMIN 으로 바꾼다.
     */
    private User ensureAdmin(User user) {
        if (!user.isAdmin()) throw new CustomException(ErrorCode.ADMIN_ONLY);
        return user;
    }
}
