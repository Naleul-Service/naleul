package com.naleul.naleul.domain.planner.scheduler;

import com.naleul.naleul.domain.fcm.dto.request.PushNotificationRequest;
import com.naleul.naleul.domain.fcm.service.PushNotificationService;
import com.naleul.naleul.domain.planner.entity.PlannerSetting;
import com.naleul.naleul.domain.planner.repository.NightlyQueryRepository;
import com.naleul.naleul.domain.planner.repository.DailyPlanRepository;
import com.naleul.naleul.domain.planner.repository.PlannerSettingRepository;
import com.naleul.naleul.domain.planner.service.DailyPlanService;
import com.naleul.naleul.domain.planner.service.NightlyPlanWorker;
import com.naleul.naleul.domain.planner.support.DayEndTrigger;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 내일 TimeTable 자동 생성 (명세 3-3, 6-6). 이월은 2026-10 부터 다음 날 아침 MorningCarryScheduler 가 한다.
 *
 * 18:00 ~ 23:50 사이 10분마다 돈다. 사용자마다 "오늘 일정이 다 끝난 시각"(DayEndTrigger)이 지났을 때 한 번만 처리한다.
 *  - 예전엔 모두 22시에 처리해서 20~23시 일정처럼 아직 하고 있는 일까지 넘겨버렸다
 *  - 대상: 최근 30일 ~ 내일 사이에 Task 가 있는 사용자 중 자동 생성이 켜진 사용자 (설정 행이 없으면 기본 ON)
 *  - 오늘 이미 만든 사용자는 daily_plan 을 미리 읽어 잠그지 않고 건너뛴다 (10분마다 도니까 가볍게)
 *  - 100명씩 끊어서 차례로 처리 (1GB 서버라 병렬로 돌리지 않음), 사용자마다 새 트랜잭션
 *  - 커밋된 뒤 푸시: "오늘 일정이 끝났어요" + 내일 배치 확인 제안
 *  - 블루그린 배포로 두 서버가 동시에 돌아도 daily_plan 선점으로 사용자당 한 번만 생성·발송된다
 *
 * planner.nightly.enabled=false 로 끌 수 있다 (예: 테스트 환경)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NightlyPlanScheduler {

    static final int PAGE_SIZE = 100;
    static final int ACTIVE_LOOKBACK_DAYS = 30;

    private final NightlyQueryRepository nightlyQueryRepository;
    private final PlannerSettingRepository plannerSettingRepository;
    private final DailyPlanRepository dailyPlanRepository;
    private final NightlyPlanWorker worker;
    private final PushNotificationService pushNotificationService;
    private final PlannerClock clock;

    @Value("${planner.nightly.enabled:true}")
    private boolean enabled;

    private final DayEndTrigger trigger = DayEndTrigger.DEFAULT;

    /** 같은 날 계속 실패하는 사용자를 10분마다 다시 시도하며 로그를 쌓지 않도록 (하루 3번까지) */
    static final int MAX_FAILURES_PER_DAY = 3;
    private final Map<Long, Integer> failures = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile LocalDate failuresDay;

    @Scheduled(cron = "${planner.nightly.cron:0 */10 18-23 * * *}", zone = "Asia/Seoul")
    public void generateTomorrowPlans() {
        if (!enabled) return;
        LocalDate today = clock.today();
        LocalDate tomorrow = today.plusDays(1);
        LocalDateTime now = clock.now();
        long started = System.currentTimeMillis();
        int processed = 0, generated = 0, failed = 0, waiting = 0, lateCarried = 0;
        if (!today.equals(failuresDay)) {
            failures.clear();
            failuresDay = today;
        }

        long cursor = 0;
        while (true) {
            List<Long> userIds = nightlyQueryRepository.findActiveUserIds(cursor,
                            today.minusDays(ACTIVE_LOOKBACK_DAYS), tomorrow,
                            today.minusDays(ACTIVE_LOOKBACK_DAYS).atStartOfDay(), today.plusDays(2).atStartOfDay(), PAGE_SIZE)
                    .stream().map(Number::longValue).toList();
            if (userIds.isEmpty()) break;
            cursor = userIds.get(userIds.size() - 1);

            Map<Long, PlannerSetting> settings = new HashMap<>();
            plannerSettingRepository.findAllById(userIds).forEach(s -> settings.put(s.getUserId(), s));
            Set<Long> doneToday = new HashSet<>();
            dailyPlanRepository.findAllByUserIdsAndPlanDate(userIds, tomorrow).forEach(p -> {
                if (DailyPlanService.nightlyDoneOn(p, today)) doneToday.add(p.getUserId());
            });
            Map<Long, Object[]> stats = new HashMap<>();
            nightlyQueryRepository.findDayEndStats(userIds, today, today.atStartOfDay(), tomorrow.atStartOfDay())
                    .forEach(r -> stats.put(((Number) r[0]).longValue(), r));

            for (Long userId : userIds) {
                PlannerSetting s = settings.get(userId);
                if (s != null && !s.isAutoGenerateEnabled()) continue; // 토글 OFF → 생성·이월·푸시 없음
                // 2026-10: 늦은 이월(23:50) 없음 — 못 한 일은 다음 날 아침 기상 시간에 옮긴다 (MorningCarryScheduler)
                if (doneToday.contains(userId)) continue;
                if (failures.getOrDefault(userId, 0) >= MAX_FAILURES_PER_DAY) continue;
                if (now.isBefore(triggerAt(today, stats.get(userId)))) {
                    waiting++;
                    continue; // 아직 오늘 일정이 남아 있음
                }
                processed++;
                try {
                    NightlyPlanWorker.Result r = worker.run(userId, today, false);
                    if (r.generated()) {
                        generated++;
                        push(r);
                    }
                } catch (RuntimeException e) {
                    failed++;
                    int n = failures.merge(userId, 1, Integer::sum);
                    if (n == 1) log.error("[Nightly] 내일 TimeTable 생성 실패 userId={}", userId, e);
                    else log.warn("[Nightly] 내일 TimeTable 생성 실패 {}회 userId={} : {}", n, userId, e.getMessage());
                }
            }
            if (userIds.size() < PAGE_SIZE) break;
        }
        if (processed > 0 || failed > 0 || lateCarried > 0) {
            log.info("[Nightly] {} 처리={} 생성={} 실패={} 대기={} 늦은이월={} 소요={}ms",
                    tomorrow, processed, generated, failed, waiting, lateCarried, System.currentTimeMillis() - started);
        }
    }

    LocalDateTime triggerAt(LocalDate today, Object[] row) {
        if (row == null) return trigger.triggerAt(today, null, 0, 0);
        return trigger.triggerAt(today, toDateTime(row[1]),
                row[2] == null ? 0 : ((Number) row[2]).longValue(),
                row[3] == null ? 0 : ((Number) row[3]).longValue());
    }

    private static LocalDateTime toDateTime(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime l) return l;
        if (v instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return LocalDateTime.parse(v.toString().replace(' ', 'T'));
    }

    private void push(NightlyPlanWorker.Result r) {
        if (!r.pushEnabled() || (r.placed() == 0 && r.carried() == 0 && r.unplaced() == 0)) return;
        String body = "내일 TimeTable을 미리 짜뒀어요. 확인해볼까요? (" + r.summary() + ")";
        try {
            pushNotificationService.sendToUser(PushNotificationRequest.builder()
                    .userId(r.userId())
                    .title("오늘 일정이 끝났어요 🌙")
                    .body(body)
                    .data(Map.of(
                            "type", "DAILY_PLAN_READY",
                            "date", clock.today().plusDays(1).toString()))
                    .build());
        } catch (RuntimeException e) {
            log.warn("[Nightly] 푸시 실패 userId={} : {}", r.userId(), e.getMessage());
        }
    }
}
