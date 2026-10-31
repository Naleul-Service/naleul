package com.naleul.naleul.domain.webBilling.scheduler;

import com.naleul.naleul.domain.webBilling.enums.WebSubscriptionStatus;
import com.naleul.naleul.domain.webBilling.repository.WebSubscriptionRepository;
import com.naleul.naleul.domain.webBilling.service.WebBillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 웹 구독 자동 결제 — 매시 17분 (토스는 정기결제 스케줄러를 제공하지 않아 직접 돌린다).
 * 구독마다 따로 트랜잭션 · 행 잠금이라, 하나가 실패해도 나머지는 계속 결제된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebBillingRenewalScheduler {

    private final WebSubscriptionRepository subscriptionRepository;
    private final WebBillingService billingService;

    @Scheduled(cron = "0 17 * * * *")
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> due = subscriptionRepository.findDueIds(
                List.of(WebSubscriptionStatus.ACTIVE, WebSubscriptionStatus.PAST_DUE), now);
        for (Long id : due) {
            try {
                billingService.renew(id);
            } catch (RuntimeException e) {
                log.error("[WebBilling] 자동 결제 처리 오류 subscriptionId={}", id, e);
            }
        }
        List<Long> ended = subscriptionRepository.findEndedCanceledIds(now);
        ended.forEach(id -> {
            try {
                billingService.expireIfEnded(id);
            } catch (RuntimeException e) {
                log.error("[WebBilling] 해지 구독 종료 처리 오류 subscriptionId={}", id, e);
            }
        });
        if (!due.isEmpty() || !ended.isEmpty()) {
            log.info("[WebBilling] 자동 결제 {}건 · 해지 종료 {}건", due.size(), ended.size());
        }
    }
}
