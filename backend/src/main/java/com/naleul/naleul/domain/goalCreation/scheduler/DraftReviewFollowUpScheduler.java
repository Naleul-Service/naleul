package com.naleul.naleul.domain.goalCreation.scheduler;

import com.naleul.naleul.domain.goalCreation.service.DraftReviewFollowUp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AI 초안 정확도 — 확정 뒤 첫 행동 · 14일 · 30일 비교 (V68, 운영 대시보드 전용).
 * 새벽 4시 41분(KST) 하루 한 번 — 다른 스케줄러와 겹치지 않게.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DraftReviewFollowUpScheduler {

    private final DraftReviewFollowUp followUp;

    @Scheduled(cron = "0 41 4 * * *", zone = "Asia/Seoul")
    public void run() {
        try {
            int firstActions = followUp.findFirstActions();
            int d14 = followUp.evaluate(14);
            int d30 = followUp.evaluate(30);
            if (firstActions + d14 + d30 > 0) {
                log.info("[DraftReview] 확정 뒤 비교 firstActions={} d14={} d30={}", firstActions, d14, d30);
            }
        } catch (RuntimeException e) {
            log.warn("[DraftReview] 확정 뒤 비교 실패 reason={}", e.getMessage());
        }
    }
}
