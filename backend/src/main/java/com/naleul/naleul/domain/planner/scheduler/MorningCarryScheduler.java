package com.naleul.naleul.domain.planner.scheduler;

import com.naleul.naleul.domain.planner.entity.PlannerSetting;
import com.naleul.naleul.domain.planner.repository.NightlyQueryRepository;
import com.naleul.naleul.domain.planner.repository.PlannerSettingRepository;
import com.naleul.naleul.domain.planner.service.NightlyPlanWorker;
import com.naleul.naleul.domain.planner.support.MorningCarryTime;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 아침 이월 (2026-10) — 어제 못 한 일을 오늘로 옮긴다. 04:00 ~ 11:50 사이 10분마다.
 *
 * 사용자마다 기상 시간(MorningCarryTime)이 지났을 때 어제 TODO 로 남은 일회성 Task 를 오늘로 옮긴다.
 *  - 자동 이월이 꺼진 사용자(planner_setting.auto_carry_enabled = false)는 건너뛴다 → 어제에 '놓침'으로 남는다
 *  - 옮길 게 없으면 기상 시간도 계산하지 않는다 (10분마다 도니까 가볍게)
 *  - 옮긴 Task 는 사라지므로 다시 돌아도 같은 Task 를 두 번 옮기지 않는다 (사용자가 되돌린 Task 도 건너뜀)
 *
 * planner.morning-carry.enabled=false 로 끌 수 있다 (예: 테스트 환경)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MorningCarryScheduler {

    static final int PAGE_SIZE = 100;
    static final int ACTIVE_LOOKBACK_DAYS = 2;

    private final NightlyQueryRepository nightlyQueryRepository;
    private final PlannerSettingRepository plannerSettingRepository;
    private final NightlyPlanWorker worker;
    private final PlannerClock clock;

    @Value("${planner.morning-carry.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${planner.morning-carry.cron:0 */10 4-11 * * *}", zone = "Asia/Seoul")
    public void carryYesterday() {
        if (!enabled) return;
        LocalDate today = clock.today();
        LocalDate yesterday = today.minusDays(1);
        LocalDateTime now = clock.now();
        int users = 0, carried = 0, failed = 0;

        long cursor = 0;
        while (true) {
            List<Long> userIds = nightlyQueryRepository.findActiveUserIds(cursor,
                            today.minusDays(ACTIVE_LOOKBACK_DAYS), today,
                            today.minusDays(ACTIVE_LOOKBACK_DAYS).atStartOfDay(), today.atStartOfDay(), PAGE_SIZE)
                    .stream().map(Number::longValue).toList();
            if (userIds.isEmpty()) break;
            cursor = userIds.get(userIds.size() - 1);

            Map<Long, PlannerSetting> settings = new HashMap<>();
            plannerSettingRepository.findAllById(userIds).forEach(s -> settings.put(s.getUserId(), s));

            for (Long userId : userIds) {
                PlannerSetting s = settings.get(userId);
                if (s != null && !s.isAutoCarryEnabled()) continue;
                try {
                    if (nightlyQueryRepository.findCarryOverCandidates(userId, yesterday,
                            yesterday.atStartOfDay(), today.atStartOfDay()).isEmpty()) continue;
                    // 기상 시간이 안 됐으면 worker 가 0 을 돌려줘요 (수면 패턴은 트랜잭션 안에서 읽어요)
                    if (now.isBefore(MorningCarryTime.at(today, null).with(MorningCarryTime.EARLIEST))) continue;
                    int n = worker.morningCarry(userId, yesterday, today);
                    if (n > 0) {
                        users++;
                        carried += n;
                    }
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("[MorningCarry] 이월 실패 userId={} : {}", userId, e.getMessage());
                }
            }
            if (userIds.size() < PAGE_SIZE) break;
        }
        if (carried > 0 || failed > 0) {
            log.info("[MorningCarry] {} → {} 사용자={} 이월={} 실패={}", yesterday, today, users, carried, failed);
        }
    }
}
