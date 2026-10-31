package com.naleul.naleul.global.scheduler;

import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PremiumExpirationScheduler {

    private final UserRepository userRepository;

    // 매일 자정 5분에 실행 (다른 배치와 시간 겹치지 않게 살짝 여유)
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void downgradeExpiredPremiumUsers() {
        List<User> expiredUsers = userRepository.findExpiredPremiumUsers(LocalDateTime.now());

        for (User user : expiredUsers) {
            user.revokePremium();     // isPremium=false, premiumExpiresAt=null
            user.downgradeToFree();   // userRole=FREE (ADMIN은 건드리지 않음)
        }

        log.info("프리미엄 만료 유저 다운그레이드 완료: {}명", expiredUsers.size());
    }
}