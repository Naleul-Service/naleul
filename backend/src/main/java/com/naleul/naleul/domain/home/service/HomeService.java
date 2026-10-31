package com.naleul.naleul.domain.home.service;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.goalProgress.entity.GoalMetricLog;
import com.naleul.naleul.domain.goalProgress.entity.GoalReflection;
import com.naleul.naleul.domain.goalProgress.repository.GoalMetricLogRepository;
import com.naleul.naleul.domain.goalProgress.repository.GoalReflectionRepository;
import com.naleul.naleul.domain.home.dto.response.HomeTodayResponse;
import com.naleul.naleul.domain.home.repository.HomeQueryRepository;
import com.naleul.naleul.domain.home.support.HomeCalculator;
import com.naleul.naleul.domain.home.support.HomeCalculator.TopicDeadline;
import com.naleul.naleul.domain.planner.repository.TimetableQueryRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.retrospective.entity.Retrospective;
import com.naleul.naleul.domain.retrospective.repository.RetrospectiveRepository;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.work.service.WorkBoardService;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 홈 "오늘" — 다가오는 마감 + 목표별 오늘 (회고 쓰기용). 계산은 HomeCalculator.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private final GoalCategoryRepository goalCategoryRepository;
    private final TimetableQueryRepository timetableQueryRepository;
    private final HomeQueryRepository homeQueryRepository;
    private final RetrospectiveRepository retrospectiveRepository;
    private final GoalMetricLogRepository metricLogRepository;
    private final GoalReflectionRepository reflectionRepository;
    private final WorkBoardService workBoardService;
    private final PlannerClock clock;

    /** 홈에서 볼 수 있는 날짜 범위 (오늘 ±1년) */
    static final int MAX_DAYS_AWAY = 366;

    public HomeTodayResponse today(Long userId) {
        return today(userId, null);
    }

    /** @param date 보고 싶은 날 (null = 오늘). 그날의 목표별 할 일 · 회고, 그날 기준 마감 */
    public HomeTodayResponse today(Long userId, LocalDate date) {
        LocalDate real = clock.today();
        LocalDate today = date == null ? real : date;
        if (Math.abs(java.time.temporal.ChronoUnit.DAYS.between(real, today)) > MAX_DAYS_AWAY) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        // 기본 생활 습관 그릇(HABIT)은 이 목록에 없다 (습관은 GET /habits)
        List<GoalCategory> goals = goalCategoryRepository.findAllByUserIdWithAll(userId);

        List<Task> due = timetableQueryRepository.findDueInRange(userId,
                today.minusDays(HomeCalculator.OVERDUE_DAYS), today.plusDays(HomeCalculator.TASK_DAYS));

        // 업무형 주제 마감 (V53) — 보드와 같은 계산(끝남 · 다음 마감)을 그대로 쓴다
        List<TopicDeadline> topics = goals.stream()
                .filter(g -> g.isRecord() && !g.isTemporary()
                        && g.getGoalCategoryStatus() == GoalCategoryStatus.IN_PROGRESS)
                .flatMap(g -> workBoardService.board(userId, g.getGoalCategoryId(), 1).timeline().stream()
                        .map(t -> new TopicDeadline(g, t)))
                .toList();

        List<Task> todayTasks = homeQueryRepository.findTouchingDay(userId,
                today.atStartOfDay(), today.plusDays(1).atStartOfDay(), today);

        List<Retrospective> retros = retrospectiveRepository.findAllByFilter(userId, null, today, today, null, null,
                PageRequest.of(0, 100, Sort.by(Sort.Direction.ASC, "retrospectiveId"))).getContent();

        return HomeCalculator.build(today, goals, due, topics, todayTasks, retros, dayRecords(userId, today));
    }

    /** 목표형 그날 기록 (수치 + 회고) — 목표 상세 "오늘 기록"과 같은 데이터 */
    private Map<Long, HomeCalculator.DayRecord> dayRecords(Long userId, LocalDate day) {
        Map<Long, BigDecimal> values = new HashMap<>();
        for (GoalMetricLog l : metricLogRepository.findAllOfUser(userId, day, day, null)) {
            values.put(l.getGoalCategory().getGoalCategoryId(), l.getValue());
        }
        Map<Long, GoalReflection> refl = new HashMap<>();
        for (GoalReflection r : reflectionRepository.findAllOfUser(userId, day, day, null)) {
            refl.put(r.getGoalCategory().getGoalCategoryId(), r);
        }
        Map<Long, HomeCalculator.DayRecord> out = new HashMap<>();
        Set<Long> ids = new HashSet<>(values.keySet());
        ids.addAll(refl.keySet());
        for (Long id : ids) {
            GoalReflection r = refl.get(id);
            out.put(id, new HomeCalculator.DayRecord(values.get(id), r != null ? r.getMood() : null, r != null ? r.getNote() : null));
        }
        return out;
    }
}
