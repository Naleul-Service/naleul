package com.naleul.naleul.domain.goalCreation.personal;

import com.naleul.naleul.domain.pattern.calculator.PatternCalculator;
import com.naleul.naleul.domain.pattern.calculator.PatternSampleSet;
import com.naleul.naleul.domain.pattern.calculator.PatternStats;
import com.naleul.naleul.domain.pattern.enums.PatternPeriod;
import com.naleul.naleul.domain.pattern.service.PatternSampleLoader;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.user.service.UserFinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사용자의 실행 역량 읽기 (최근 4주 나의 패턴 계산 + 지금 진행 중인 루틴).
 *
 * 목표 만들기 대화(인터뷰 여러 번 + 초안)에서 여러 번 불리므로 10분 동안 메모리에 둔다.
 * 계산이 실패해도 목표 만들기는 계속돼야 해서, 실패하면 "기록 없음"으로 본다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CapacityProfileReader {

    static final Duration TTL = Duration.ofMinutes(10);
    static final int CACHE_MAX = 5000;

    private final PatternSampleLoader sampleLoader;
    private final RoutineRepository routineRepository;
    private final UserFinder userFinder;
    private final PlannerClock clock;

    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(CapacityProfile profile, LocalDateTime at) {
    }

    public CapacityProfile read(Long userId) {
        LocalDateTime now = clock.now();
        Cached c = cache.get(userId);
        if (c != null && c.at().plus(TTL).isAfter(now)) return c.profile();

        CapacityProfile profile;
        try {
            LocalDate today = clock.today();
            PatternSampleSet set = sampleLoader.load(userId, PatternPeriod.RECENT_4W, today, now);
            PatternStats stats = PatternCalculator.calculate(set);
            List<Integer> loads = routineRepository.findAllByUser(userFinder.findUser(userId)).stream()
                    .filter(r -> RoutineLoad.isActive(r, today))
                    .map(RoutineLoad::weeklyMinutes)
                    .toList();
            profile = CapacityProfiles.from(set, stats, loads);
        } catch (RuntimeException e) {
            log.warn("[Capacity] 실행 역량 계산 실패 → 기록 없음으로 계획 userId={}", userId, e);
            profile = CapacityProfile.empty();
        }
        if (cache.size() > CACHE_MAX) cache.clear();
        cache.put(userId, new Cached(profile, now));
        return profile;
    }
}
