package com.naleul.naleul.domain.goalCreation.scheduler;

import com.naleul.naleul.domain.goalCreation.entity.GoalCreationSession;
import com.naleul.naleul.domain.goalCreation.reader.GoalCreationSessionReader;
import com.naleul.naleul.domain.goalCreation.repository.GoalCreationSessionRepository;
import com.naleul.naleul.domain.goalCreation.support.GoalClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 7일 지난 미완료 세션을 ABANDONED로 정리 (명세 2-1).
 * 사용량이 적은 새벽 4시 7분(KST)에 실행. (정각을 피해 다른 스케줄러와 겹치지 않게)
 *
 * ⚠️ 블루그린 배포 중 잠깐 8081/8082가 동시에 떠 있으면 두 번 실행될 수 있지만,
 *    이미 ABANDONED면 조회 대상에서 빠지므로 결과는 같다(멱등).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoalCreationSessionExpireScheduler {

    private final GoalCreationSessionRepository sessionRepository;
    private final GoalClock clock;

    @Scheduled(cron = "0 7 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void abandonExpiredSessions() {
        List<GoalCreationSession> expired = sessionRepository
                .findAllByStatusInAndExpiresAtBefore(GoalCreationSessionReader.ACTIVE_STATUSES, clock.now());
        expired.forEach(s -> s.abandon(com.naleul.naleul.domain.goalCreation.enums.AbandonReason.EXPIRED));
        if (!expired.isEmpty()) {
            log.info("[GoalCreation] 만료 세션 정리 {}건", expired.size());
        }
    }
}
