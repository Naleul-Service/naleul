package com.naleul.naleul.domain.home.service;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalProgress.entity.GoalMetricLog;
import com.naleul.naleul.domain.goalProgress.entity.GoalReflection;
import com.naleul.naleul.domain.goalProgress.repository.GoalMetricLogRepository;
import com.naleul.naleul.domain.goalProgress.repository.GoalReflectionRepository;
import com.naleul.naleul.domain.home.dto.response.ReflectionFeedResponse;
import com.naleul.naleul.domain.home.dto.response.ReflectionFeedResponse.Entry;
import com.naleul.naleul.domain.home.dto.response.ReflectionFeedResponse.Kind;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.retrospective.entity.Retrospective;
import com.naleul.naleul.domain.retrospective.repository.RetrospectiveRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 회고 모아보기 — 목표 상세(오늘 기록) · 홈 · 업무형 보드에 흩어져 있던 회고를 날짜별로 한곳에서.
 *
 * 목표형 기록은 goal_reflection(기분 · 한 줄 회고) + goal_metric_log(수치)를 목표 · 날짜로 합쳐 한 줄로,
 * 업무형 회고 글은 retrospective 한 건이 한 줄이에요.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReflectionFeedService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 92;
    private static final int RETRO_LIMIT = 500;

    private final GoalReflectionRepository reflectionRepository;
    private final GoalMetricLogRepository metricLogRepository;
    private final RetrospectiveRepository retrospectiveRepository;
    private final PlannerClock clock;

    /**
     * @param before 이 날짜 "전날"까지 (없으면 오늘까지)
     * @param days   몇 일치 (기본 30, 최대 92)
     */
    public ReflectionFeedResponse feed(Long userId, LocalDate before, Integer days, Long goalCategoryId) {
        int n = days == null ? DEFAULT_DAYS : days;
        if (n < 1 || n > MAX_DAYS) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        LocalDate to = (before == null ? clock.today().plusDays(1) : before).minusDays(1);
        LocalDate from = to.minusDays(n - 1L);

        // 목표 · 날짜별로 수치와 회고를 합쳐요
        Map<String, Entry> checkIns = new LinkedHashMap<>();
        for (GoalReflection r : reflectionRepository.findAllOfUser(userId, from, to, goalCategoryId)) {
            checkIns.put(key(r.getGoalCategory(), r.getReflectDate()),
                    checkIn(r.getGoalCategory(), r.getReflectDate(), r.getMood() != null ? r.getMood().name() : null,
                            r.getNote(), null));
        }
        for (GoalMetricLog l : metricLogRepository.findAllOfUser(userId, from, to, goalCategoryId)) {
            String k = key(l.getGoalCategory(), l.getLogDate());
            Entry prev = checkIns.get(k);
            checkIns.put(k, checkIn(l.getGoalCategory(), l.getLogDate(),
                    prev != null ? prev.mood() : null, prev != null ? prev.note() : null, l.getValue()));
        }

        List<Entry> entries = new ArrayList<>(checkIns.values());
        List<Retrospective> retros = retrospectiveRepository.findAllByFilter(userId, null, from, to, goalCategoryId, null,
                PageRequest.of(0, RETRO_LIMIT, Sort.by(Sort.Direction.DESC, "reviewDate", "retrospectiveId"))).getContent();
        for (Retrospective r : retros) {
            GoalCategory g = r.getGoalCategory();
            entries.add(new Entry(Kind.RETRO, r.getReviewDate(), g.getGoalCategoryId(), g.getGoalCategoryName(),
                    g.getEmoji(), colorOf(g), modeOf(g), r.getRetrospectiveId(),
                    r.getReviewType() != null ? r.getReviewType().name() : null, null, r.getContent(), null, null, null));
        }

        // 최근 날짜 먼저 → 같은 날은 목표 id → 기록(CHECK_IN)이 회고 글보다 먼저 → 회고 글은 쓴 순서
        entries.sort(Comparator.comparing(Entry::date, Comparator.reverseOrder())
                .thenComparing(Entry::goalCategoryId)
                .thenComparing(Entry::kind)
                .thenComparing(e -> e.retrospectiveId() == null ? 0L : e.retrospectiveId()));

        boolean hasMore = reflectionRepository.existsBefore(userId, from, goalCategoryId)
                || metricLogRepository.existsBefore(userId, from, goalCategoryId)
                || retrospectiveRepository.findAllByFilter(userId, null, null, from.minusDays(1), goalCategoryId, null,
                        PageRequest.of(0, 1)).hasContent();
        return new ReflectionFeedResponse(from, to, entries, hasMore);
    }

    private static Entry checkIn(GoalCategory g, LocalDate date, String mood, String note, BigDecimal value) {
        boolean hasMetric = g.getTargetValue() != null && g.getStartValue() != null;
        return new Entry(Kind.CHECK_IN, date, g.getGoalCategoryId(), g.getGoalCategoryName(), g.getEmoji(), colorOf(g),
                modeOf(g), null, null, mood, note, value,
                hasMetric ? g.getMetricName() : null, hasMetric ? g.getMetricUnit() : null);
    }

    private static String key(GoalCategory g, LocalDate d) {
        return g.getGoalCategoryId() + "|" + d;
    }

    private static String colorOf(GoalCategory g) {
        return g.getUserColor() != null ? g.getUserColor().getColorCode() : null;
    }

    private static String modeOf(GoalCategory g) {
        return g.getGoalMode() != null ? g.getGoalMode().name() : null;
    }
}
