package com.naleul.naleul.domain.goalOutcome.service;

import com.naleul.naleul.domain.goalCategory.kind.GoalSubType;
import com.naleul.naleul.domain.goalCreation.personal.AchieverSummary;
import com.naleul.naleul.domain.goalOutcome.entity.GoalOutcome;
import com.naleul.naleul.domain.goalOutcome.enums.OutcomeResult;
import com.naleul.naleul.domain.goalOutcome.repository.GoalOutcomeRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 같은 2단계 카테고리 달성자 익명 집계 → 계획 AI 의 출발점 · 초안 화면 "나에게 맞춘 점".
 *
 * 개인 보호:
 *  - 개인 기록 원문은 쓰지 않고 중앙값만 낸다
 *  - 달성한 목표가 MIN_ACHIEVED 개 미만이면 아예 만들지 않는다 (몇 명의 기록으로 특정 사용자를 짐작하지 못하게)
 *  - 최근 2년 안에 끝난 목표만
 * 집계는 하루에도 크게 바뀌지 않아 카테고리별로 1시간 메모리에 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AchieverStats {

    public static final int MIN_ACHIEVED = 30;
    static final int LOOKBACK_DAYS = 730;
    static final Duration TTL = Duration.ofHours(1);

    private final GoalOutcomeRepository outcomeRepository;
    private final PlannerClock clock;

    private final Map<GoalSubType, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(Optional<AchieverSummary> summary, LocalDateTime at) {
    }

    public Optional<AchieverSummary> forSubType(GoalSubType subType) {
        if (subType == null || subType.isCustom()) return Optional.empty();   // "기타"는 사람마다 달라 묶지 않는다
        LocalDateTime now = clock.now();
        Cached c = cache.get(subType);
        if (c != null && c.at().plus(TTL).isAfter(now)) return c.summary();
        Optional<AchieverSummary> summary;
        try {
            summary = summarize(subType.getLabel(), outcomeRepository.findForStats(subType, now.minusDays(LOOKBACK_DAYS))
                    .stream().map(Row::of).toList());
        } catch (RuntimeException e) {
            log.warn("[AchieverStats] 집계 실패 subType={}", subType, e);
            summary = Optional.empty();
        }
        cache.put(subType, new Cached(summary, now));
        return summary;
    }

    /** 집계에 쓰는 값만 (순수 계산 테스트용) */
    public record Row(OutcomeResult result, Integer plannedDays, int weeklyRoutineMinutes, int routineCount) {
        static Row of(GoalOutcome o) {
            return new Row(o.getResult(), o.getPlannedDays(), o.getWeeklyRoutineMinutes(), o.getRoutineCount());
        }
    }

    /** 순수 계산 — 테스트 대상 */
    static Optional<AchieverSummary> summarize(String label, List<Row> rows) {
        List<Row> achieved = rows.stream().filter(r -> r.result() == OutcomeResult.ACHIEVED).toList();
        if (achieved.size() < MIN_ACHIEVED) return Optional.empty();
        return Optional.of(new AchieverSummary(label, achieved.size(), rows.size(),
                median(achieved.stream().map(Row::plannedDays).filter(d -> d != null && d > 0).mapToInt(Integer::intValue).sorted().toArray()),
                median(achieved.stream().mapToInt(Row::weeklyRoutineMinutes).filter(m -> m > 0).sorted().toArray()),
                median(achieved.stream().mapToInt(Row::routineCount).filter(n -> n > 0).sorted().toArray())));
    }

    static int median(int[] sorted) {
        if (sorted.length == 0) return 0;
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : Math.round((sorted[mid - 1] + sorted[mid]) / 2f);
    }
}
