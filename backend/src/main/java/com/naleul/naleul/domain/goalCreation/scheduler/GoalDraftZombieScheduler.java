package com.naleul.naleul.domain.goalCreation.scheduler;

import com.naleul.naleul.domain.goalCreation.service.GoalDraftTxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 5분마다 좀비 초안 정리.
 *
 * 좀비가 생기는 경우: 블루그린 배포로 구 버전이 종료되며 진행 중이던 생성 작업이 사라짐,
 * OOM/크래시로 재시작됨 등. 이때 초안은 GENERATING, 세션도 GENERATING에 멈춘다.
 * 폴링(GET draft)에서도 정리하지만, 사용자가 앱을 꺼버리면 아무도 폴링하지 않으므로
 * 세션이 영원히 GENERATING에 갇혀 "다시 생성"도 못 하게 된다 → 스케줄러로 보완.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoalDraftZombieScheduler {

    private final GoalDraftTxService draftTxService;

    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void failZombieDrafts() {
        int count = draftTxService.failZombies();
        if (count > 0) {
            log.warn("[GoalDraft] 좀비 초안 {}건 FAILED(AI_TIMEOUT) 처리", count);
        }
    }
}
