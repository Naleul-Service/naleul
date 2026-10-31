package com.naleul.naleul.domain.admin.support;

import com.naleul.naleul.domain.admin.config.AdminDashboardProperties;
import com.naleul.naleul.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * (선택) 서버가 뜰 때 admin.dashboard.admin-user-ids 의 사용자를 user_role = ADMIN 으로 맞춘다 (2026-10-24).
 *
 * 기본은 꺼져 있다 — admin-user-ids 기본값(1,2)이 환경마다 다른 사람일 수 있고,
 * ADMIN 은 결제 없이 Pro 혜택(플랜 바꿔 보기 기본 Pro)을 받기 때문에 자동으로 올리면 위험하다.
 * 켜려면 ADMIN_SYNC_ROLE_ON_STARTUP=true (admin.dashboard.sync-role-on-startup). 운영진에서 빼는 건 DB 에서 직접 한다.
 *
 * 한 사람씩 따로 저장해서, 한 명이 실패해도 나머지와 서버 기동에는 영향이 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "admin.dashboard", name = "sync-role-on-startup", havingValue = "true")
public class AdminRoleSync implements ApplicationRunner {

    private final AdminDashboardProperties properties;
    private final UserRepository userRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (properties.adminUserIds() == null) return;
        for (Long id : properties.adminUserIds()) {
            try {
                userRepository.findById(id).filter(u -> !u.isAdmin()).ifPresent(u -> {
                    u.promoteToAdmin();
                    userRepository.save(u);
                    log.info("[AdminRoleSync] userId={} → ADMIN", id);
                });
            } catch (Exception e) {
                log.warn("[AdminRoleSync] userId={} 운영진 역할 맞추기 실패: {}", id, e.getMessage());
            }
        }
    }
}
