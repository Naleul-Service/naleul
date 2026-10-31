package com.naleul.naleul.domain.pattern.scheduler;

import com.naleul.naleul.domain.pattern.repository.PatternReportRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오래된 패턴 리포트 캐시 정리 (명세 4 · 6-1). 매일 04:00, 기준일이 7일보다 오래된 행 삭제.
 * 블루그린으로 두 서버가 동시에 돌아도 같은 행을 지울 뿐이라 문제없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatternReportCleanupScheduler {

    static final int KEEP_DAYS = 7;

    private final PatternReportRepository patternReportRepository;
    private final PlannerClock clock;

    @Scheduled(cron = "${pattern.cache.cleanup-cron:0 0 4 * * *}", zone = "Asia/Seoul")
    @Transactional
    public void cleanup() {
        int deleted = patternReportRepository.deleteOlderThan(clock.today().minusDays(KEEP_DAYS));
        if (deleted > 0) log.info("[Pattern] 오래된 리포트 캐시 {}건 삭제", deleted);
    }
}
