package com.naleul.naleul.domain.goalProgress.service;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalProgress.dto.request.GoalExtraMetricRequest;
import com.naleul.naleul.domain.goalProgress.dto.request.GoalMetricLogRequest;
import com.naleul.naleul.domain.goalProgress.dto.response.GoalProgressResponse;
import com.naleul.naleul.domain.goalProgress.entity.GoalExtraMetric;
import com.naleul.naleul.domain.goalProgress.entity.GoalExtraMetricLog;
import com.naleul.naleul.domain.goalProgress.repository.GoalExtraMetricLogRepository;
import com.naleul.naleul.domain.goalProgress.repository.GoalExtraMetricRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 목표형 목표의 추가 그래프 만들기 · 고치기 · 지우기 · 기록 (V52).
 * 응답은 기존 "진행" 데이터 전체(GoalProgressResponse)라서 화면은 받은 값으로 바로 다시 그린다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GoalExtraMetricService {

    static final int MAX_EXTRA_METRICS = 5;

    private final GoalExtraMetricRepository metricRepository;
    private final GoalExtraMetricLogRepository logRepository;
    private final GoalProgressService goalProgressService;
    private final CategoryFinder categoryFinder;
    private final UserFinder userFinder;
    private final PlannerClock clock;

    public GoalProgressResponse create(Long userId, Long goalCategoryId, GoalExtraMetricRequest request) {
        GoalCategory goal = achievementGoal(userId, goalCategoryId);
        if (request.name() == null || request.name().isBlank()
                || request.startValue() == null || request.targetValue() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        validateValues(request.startValue(), request.targetValue());
        List<GoalExtraMetric> existing = metricRepository.findAllOfGoal(goalCategoryId);
        if (existing.size() >= MAX_EXTRA_METRICS) throw new CustomException(ErrorCode.GOAL_EXTRA_METRIC_LIMIT_EXCEEDED);
        int order = existing.stream().mapToInt(GoalExtraMetric::getSortOrder).max().orElse(-1) + 1;
        metricRepository.save(GoalExtraMetric.create(goal, request.name(), request.unit(),
                request.startValue(), request.targetValue(), order));
        return goalProgressService.getProgress(userId, goalCategoryId);
    }

    public GoalProgressResponse update(Long userId, Long goalCategoryId, Long metricId, GoalExtraMetricRequest request) {
        achievementGoal(userId, goalCategoryId);
        GoalExtraMetric metric = find(goalCategoryId, metricId);
        BigDecimal start = request.startValue() != null ? request.startValue() : metric.getStartValue();
        BigDecimal target = request.targetValue() != null ? request.targetValue() : metric.getTargetValue();
        validateValues(start, target);
        boolean startChanged = request.startValue() != null && metric.getStartValue().compareTo(request.startValue()) != 0;
        metric.update(request.name(), request.unit(), request.startValue(), request.targetValue());
        if (startChanged) syncCurrent(metric);   // 기록이 없으면 현재 값 = 시작값
        return goalProgressService.getProgress(userId, goalCategoryId);
    }

    public GoalProgressResponse delete(Long userId, Long goalCategoryId, Long metricId) {
        achievementGoal(userId, goalCategoryId);
        find(goalCategoryId, metricId).delete();
        return goalProgressService.getProgress(userId, goalCategoryId);
    }

    /** 같은 날짜 기록이 있으면 덮어쓴다. 날짜가 없으면 오늘, 미래는 안 된다 */
    public GoalProgressResponse record(Long userId, Long goalCategoryId, Long metricId, GoalMetricLogRequest request) {
        achievementGoal(userId, goalCategoryId);
        GoalExtraMetric metric = find(goalCategoryId, metricId);
        LocalDate date = request.date() != null ? request.date() : clock.today();
        if (date.isAfter(clock.today())) throw new CustomException(ErrorCode.GOAL_METRIC_LOG_FUTURE_DATE);
        logRepository.findOne(metricId, date).ifPresentOrElse(
                l -> l.change(request.value(), request.memo()),
                () -> logRepository.save(GoalExtraMetricLog.create(metric, date, request.value(), request.memo())));
        logRepository.flush();
        syncCurrent(metric);
        return goalProgressService.getProgress(userId, goalCategoryId);
    }

    public GoalProgressResponse deleteLog(Long userId, Long goalCategoryId, Long metricId, LocalDate date) {
        achievementGoal(userId, goalCategoryId);
        GoalExtraMetric metric = find(goalCategoryId, metricId);
        GoalExtraMetricLog log = logRepository.findOne(metricId, date)
                .orElseThrow(() -> new CustomException(ErrorCode.GOAL_METRIC_LOG_NOT_FOUND));
        logRepository.delete(log);
        logRepository.flush();
        syncCurrent(metric);
        return goalProgressService.getProgress(userId, goalCategoryId);
    }

    // ── 내부 ─────────────────────────────────────────────

    private GoalCategory achievementGoal(Long userId, Long goalCategoryId) {
        GoalCategory goal = categoryFinder.findGoalCategory(goalCategoryId, userFinder.findUser(userId));
        if (!goal.isAchievement()) throw new CustomException(ErrorCode.GOAL_ACHIEVEMENT_ONLY);
        return goal;
    }

    private GoalExtraMetric find(Long goalCategoryId, Long metricId) {
        return metricRepository.findInGoal(metricId, goalCategoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.GOAL_EXTRA_METRIC_NOT_FOUND));
    }

    private static void validateValues(BigDecimal start, BigDecimal target) {
        if (start.compareTo(target) == 0) throw new CustomException(ErrorCode.GOAL_EXTRA_METRIC_INVALID);
    }

    /** 현재 값 = 가장 최근 날짜 기록 (없으면 시작값) */
    private void syncCurrent(GoalExtraMetric metric) {
        metric.updateCurrentValue(logRepository.findFirstByMetric_GoalMetricIdOrderByLogDateDesc(metric.getGoalMetricId())
                .map(GoalExtraMetricLog::getValue)
                .orElse(metric.getStartValue()));
    }
}
