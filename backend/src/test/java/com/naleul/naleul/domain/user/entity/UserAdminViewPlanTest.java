package com.naleul.naleul.domain.user.entity;

import com.naleul.naleul.domain.user.enums.AdminViewPlan;
import com.naleul.naleul.domain.user.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserAdminViewPlanTest {

    private User user(UserRole role) {
        return User.builder().userName("유림").userEmail("a@b.c").userRole(role).build();
    }

    @Test
    @DisplayName("운영진은 결제 없이도 기본 Pro, Free 로 바꾸면 Free 제한을 받는다")
    void adminFollowsViewPlan() {
        User admin = user(UserRole.ADMIN);
        assertThat(admin.adminViewPlanOrNull()).isEqualTo(AdminViewPlan.PRO);
        assertThat(admin.isCurrentlyPremium()).isTrue();

        admin.changeAdminViewPlan(AdminViewPlan.FREE);
        assertThat(admin.isCurrentlyPremium()).isFalse();

        admin.changeAdminViewPlan(AdminViewPlan.PRO);
        assertThat(admin.isCurrentlyPremium()).isTrue();
    }

    @Test
    @DisplayName("운영진이 결제하거나 만료돼도 운영진 권한은 그대로")
    void billingKeepsAdmin() {
        User admin = user(UserRole.ADMIN);
        admin.grantPremium(LocalDateTime.now().plusDays(30));
        assertThat(admin.getUserRole()).isEqualTo(UserRole.ADMIN);
        admin.revokePremium();
        admin.downgradeToFree();
        assertThat(admin.getUserRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("일반 사용자는 플랜 바꿔 보기가 무시되고 결제 상태만 따른다")
    void normalUserIgnoresViewPlan() {
        User free = user(UserRole.FREE);
        free.changeAdminViewPlan(AdminViewPlan.PRO);
        assertThat(free.adminViewPlanOrNull()).isNull();
        assertThat(free.isCurrentlyPremium()).isFalse();
        free.grantPremium(null);
        assertThat(free.getUserRole()).isEqualTo(UserRole.PRO);
        assertThat(free.isCurrentlyPremium()).isTrue();
    }
}
