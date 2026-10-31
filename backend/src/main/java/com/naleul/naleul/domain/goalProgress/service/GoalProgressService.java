package com.naleul.naleul.domain.goalProgress.service;

import com.naleul.naleul.domain.goalProgress.entity.GoalExtraMetric;
import com.naleul.naleul.domain.goalProgress.entity.GoalExtraMetricLog;
import com.naleul.naleul.domain.goalProgress.repository.GoalExtraMetricLogRepository;
import com.naleul.naleul.domain.goalProgress.repository.GoalExtraMetricRepository;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalProgress.dto.request.GoalCheckInRequest;
import com.naleul.naleul.domain.goalProgress.dto.request.GoalMetricLogRequest;
import com.naleul.naleul.domain.goalProgress.dto.response.GoalProgressResponse;
import com.naleul.naleul.domain.goalProgress.dto.response.GoalProgressResponse.Log;
import com.naleul.naleul.domain.goalProgress.entity.GoalMetricLog;
import com.naleul.naleul.domain.goalOutcome.service.GoalOutcomeRecorder;
import com.naleul.naleul.domain.goalProgress.entity.GoalReflection;
import com.naleul.naleul.domain.goalProgress.repository.GoalMetricLogRepository;
import com.naleul.naleul.domain.goalProgress.repository.GoalReflectionRepository;
import com.naleul.naleul.domain.goalProgress.service.GoalProgressCalculator.MetricInput;
import com.naleul.naleul.domain.goalProgress.service.GoalProgressCalculator.MilestoneInput;
import com.naleul.naleul.domain.goalProgress.service.GoalProgressCalculator.TaskInput;
import com.naleul.naleul.domain.planner.repository.TimetableQueryRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * 목표 진행 도표 조회 + 수치 기록(오늘 몸무게 등).
 * 기록하면 목표의 currentValue 도 "가장 최근 날짜의 기록"으로 맞춘다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GoalProgressService {

    private final GoalMetricLogRepository logRepository;
    private final TimetableQueryRepository taskQueryRepository;
    private final CategoryFinder categoryFinder;
    private final UserFinder userFinder;
    private final PlannerClock clock;
    private final GoalReflectionRepository reflectionRepository;

    /** 목표 결과 · 주간 점검 (V47) — 기존 테스트 생성자 호환을 위해 필드 주입 */
    @Autowired(required = false)
    private GoalOutcomeRecorder outcomeRecorder;
    @Autowired(required = false)
    private GoalAdjustmentService adjustmentService;
    /** 추가 그래프 (V52) — 기존 테스트 생성자 호환을 위해 필드 주입 */
    @Autowired(required = false)
    private GoalExtraMetricRepository extraMetricRepository;
    @Autowired(required = false)
    private GoalExtraMetricLogRepository extraMetricLogRepository;

    /** 화면에 내려줄 최근 회고 기간 · 개수 */
    private static final int REFLECTION_DAYS = 60;
    private static final int REFLECTION_MAX = 30;

    @Transactional(readOnly = true)
    public GoalProgressResponse getProgress(Long userId, Long goalCategoryId) {
        User user = userFinder.findUser(userId);
        GoalCategory goal = categoryFinder.findGoalCategory(goalCategoryId, user);
        return build(userId, goal);
    }

    /** 같은 날짜 기록이 있으면 덮어쓴다 */
    public GoalProgressResponse record(Long userId, Long goalCategoryId, GoalMetricLogRequest request) {
        User user = userFinder.findUser(userId);
        GoalCategory goal = categoryFinder.findGoalCategory(goalCategoryId, user);
        LocalDate date = request.date() != null ? request.date() : clock.today();
        upsert(goal, user, date, request.value(), request.memo());
        return build(userId, goal);
    }

    /**
     * 오늘 기록 + 회고 (목표 상세 "오늘 기록" 카드).
     * 수치는 수치 목표일 때만 저장하고, 회고(무엇을 했는지 · 어땠는지)는 모든 목표에 남길 수 있다.
     */
    public GoalProgressResponse checkIn(Long userId, Long goalCategoryId, GoalCheckInRequest request) {
        User user = userFinder.findUser(userId);
        GoalCategory goal = categoryFinder.findGoalCategory(goalCategoryId, user);
        LocalDate date = request.date() != null ? request.date() : clock.today();
        if (date.isAfter(clock.today())) {
            throw new CustomException(ErrorCode.GOAL_METRIC_LOG_FUTURE_DATE);
        }
        boolean hasMetric = goal.getTargetValue() != null && goal.getStartValue() != null;
        boolean emptyReflection = GoalReflection.isEmpty(request.mood(), request.note());
        boolean hasValue = request.value() != null && hasMetric;
        boolean hadReflection = reflectionRepository.findOne(goalCategoryId, date).isPresent();
        if (!hasValue && emptyReflection && !hadReflection) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (hasValue) upsert(goal, user, date, request.value(), null);

        reflectionRepository.findOne(goalCategoryId, date).ifPresentOrElse(
                r -> {
                    if (emptyReflection) reflectionRepository.delete(r);
                    else r.change(request.mood(), request.note());
                },
                () -> {
                    if (!emptyReflection) {
                        reflectionRepository.save(GoalReflection.create(goal, user, date, request.mood(), request.note()));
                    }
                });
        reflectionRepository.flush();
        return build(userId, goal);
    }

    public GoalProgressResponse delete(Long userId, Long goalCategoryId, LocalDate date) {
        User user = userFinder.findUser(userId);
        GoalCategory goal = categoryFinder.findGoalCategory(goalCategoryId, user);
        GoalMetricLog log = logRepository.findOne(goalCategoryId, date)
                .orElseThrow(() -> new CustomException(ErrorCode.GOAL_METRIC_LOG_NOT_FOUND));
        logRepository.delete(log);
        logRepository.flush();
        syncCurrentValue(goal);
        return build(userId, goal);
    }

    /**
     * 목표 헤더에서 "현재 값"을 직접 고쳤을 때 오늘 기록으로도 남긴다 (도표와 숫자가 어긋나지 않게).
     * 수치 목표가 아니면 아무것도 하지 않는다 (예외를 던지면 헤더 수정까지 롤백되므로 조용히 넘어감).
     */
    public void recordFromHeader(GoalCategory goal, User user, BigDecimal value) {
        if (value == null || goal.getTargetValue() == null || goal.getStartValue() == null) return;
        upsert(goal, user, clock.today(), value, null);
    }

    // ── 내부 ──────────────────────────────────────────────

    private void upsert(GoalCategory goal, User user, LocalDate date, BigDecimal value, String memo) {
        if (goal.getTargetValue() == null || goal.getStartValue() == null) {
            throw new CustomException(ErrorCode.GOAL_METRIC_NOT_SET);
        }
        if (date.isAfter(clock.today())) {
            throw new CustomException(ErrorCode.GOAL_METRIC_LOG_FUTURE_DATE);
        }
        logRepository.findOne(goal.getGoalCategoryId(), date).ifPresentOrElse(
                log -> log.change(value, memo != null ? memo : log.getMemo()),   // 메모를 안 보내면 예전 메모 유지
                () -> logRepository.save(GoalMetricLog.create(goal, user, date, value, memo)));
        logRepository.flush();
        syncCurrentValue(goal);
    }

    /** currentValue = 가장 최근 날짜 기록 (기록이 없으면 시작값) */
    private void syncCurrentValue(GoalCategory goal) {
        BigDecimal latest = logRepository.findAllOfGoal(goal.getGoalCategoryId()).stream()
                .max(Comparator.comparing(GoalMetricLog::getLogDate))
                .map(GoalMetricLog::getValue)
                .orElse(goal.getStartValue());
        goal.updateCurrentValue(latest);
    }

    private GoalProgressResponse build(Long userId, GoalCategory goal) {
        LocalDate today = clock.today();
        LocalDate start = goal.getGoalCategoryStartDate();
        LocalDate end = goal.getGoalCategoryEndDate();

        List<Log> logs = logRepository.findAllOfGoal(goal.getGoalCategoryId()).stream()
                .map(l -> new Log(l.getLogDate(), l.getValue(), l.getMemo()))
                .toList();
        List<MilestoneInput> milestones = goal.getMilestones().stream()
                .map(ms -> new MilestoneInput(ms.getTitle(), ms.getDueDate(), ms.getTargetValue()))
                .toList();
        MetricInput metric = new MetricInput(goal.getMetricName(), goal.getMetricUnit(),
                goal.getStartValue(), goal.getCurrentValue(), goal.getTargetValue());

        List<Task> goalTasks = taskQueryRepository.findAllOfGoal(userId, goal.getGoalCategoryId());
        List<TaskInput> tasks = goalTasks.stream()
                .map(GoalProgressService::toInput)
                .toList();

        int elapsed = start == null ? 0 : (int) Math.max(0, ChronoUnit.DAYS.between(start, today));
        Integer total = start == null || end == null ? null : (int) ChronoUnit.DAYS.between(start, end);

        return new GoalProgressResponse(
                goal.getGoalCategoryId(), today, start, end,
                total == null ? elapsed : Math.min(elapsed, total), total,
                // 수치 계획선은 수치 마감일까지 (따로 안 정했으면 목표 종료일 — 2026-10 V58)
                GoalProgressCalculator.metric(today, start, goal.effectiveMetricTargetDate(), metric, milestones, logs),
                GoalProgressCalculator.tasks(today, start, end, tasks),
                reflectionRepository.findRecent(goal.getGoalCategoryId(), today.minusDays(REFLECTION_DAYS)).stream()
                        .limit(REFLECTION_MAX)
                        .map(r -> new GoalProgressResponse.Reflection(r.getReflectDate(),
                                r.getMood() != null ? r.getMood().name() : null, r.getNote()))
                        .toList(),
                outcome(goal),
                adjustment(userId, goal, goalTasks),
                extraMetrics(goal, today, start, end));
    }

    /** 추가 그래프 (V52) — 목표 기간 위의 직선 계획선 (마일스톤 없이) */
    private List<GoalProgressResponse.ExtraMetric> extraMetrics(GoalCategory goal, LocalDate today,
                                                                LocalDate start, LocalDate end) {
        if (extraMetricRepository == null || extraMetricLogRepository == null) return List.of();
        List<GoalExtraMetric> metrics = extraMetricRepository.findAllOfGoal(goal.getGoalCategoryId());
        if (metrics.isEmpty()) return List.of();
        java.util.Map<Long, List<Log>> logsByMetric = new java.util.HashMap<>();
        for (GoalExtraMetricLog l : extraMetricLogRepository.findAllOf(metrics.stream().map(GoalExtraMetric::getGoalMetricId).toList())) {
            logsByMetric.computeIfAbsent(l.getMetric().getGoalMetricId(), k -> new java.util.ArrayList<>())
                    .add(new Log(l.getLogDate(), l.getValue(), l.getMemo()));
        }
        return metrics.stream()
                .map(m -> new GoalProgressResponse.ExtraMetric(m.getGoalMetricId(), m.getSortOrder(),
                        GoalProgressCalculator.metric(today, start, end,
                                new MetricInput(m.getMetricName(), m.getMetricUnit(), m.getStartValue(),
                                        m.getCurrentValue(), m.getTargetValue()),
                                List.of(), logsByMetric.getOrDefault(m.getGoalMetricId(), List.of()))))
                .toList();
    }

    private GoalProgressResponse.Outcome outcome(GoalCategory goal) {
        if (outcomeRecorder == null) return null;
        return outcomeRecorder.find(goal.getGoalCategoryId())
                .map(o -> new GoalProgressResponse.Outcome(o.getResult().name(), o.getSource().name(),
                        o.getMetricPercent(), o.getRoutineRate(), o.getTaskRate(), o.isMetricUnrecorded(),
                        o.getAutoResult() != null ? o.getAutoResult().name() : null,
                        o.getAutoReason() != null ? o.getAutoReason().name() : null,
                        o.autoMessage()))
                .orElse(null);
    }

    private GoalProgressResponse.Adjustment adjustment(Long userId, GoalCategory goal, List<Task> goalTasks) {
        if (adjustmentService == null) return null;
        try {
            return adjustmentService.find(userId, goal, goalTasks)
                    .map(s -> new GoalProgressResponse.Adjustment(s.key(), s.direction().name(), s.routineId(),
                            s.routineName(), s.fromDays(), s.toDays(), s.day().name(),
                            s.previousWeekRate(), s.lastWeekRate(), s.message()))
                    .orElse(null);
        } catch (RuntimeException e) {
            // 점검 제안 계산이 실패해도 진행 화면은 떠야 한다
            log.warn("[GoalAdjustment] 제안 계산 실패 goalCategoryId={}", goal.getGoalCategoryId(), e);
            return null;
        }
    }

    /** 하기로 한 날: 루틴 날짜 → 권장일 → 계획 시각 → 마감일. 완료한 날: 완료 시각 → 실제 종료 → 하기로 한 날 */
    static TaskInput toInput(Task t) {
        LocalDate planned = t.getRoutineDate() != null ? t.getRoutineDate()
                : t.getScheduledDate() != null ? t.getScheduledDate()
                : t.getPlannedStartAt() != null ? t.getPlannedStartAt().toLocalDate()
                : t.getDueDate();
        LocalDate done = null;
        if (t.getTaskStatus() == TaskStatus.COMPLETED) {
            done = t.getCompletedAt() != null ? t.getCompletedAt().toLocalDate()
                    : t.getActualEndAt() != null ? t.getActualEndAt().toLocalDate()
                    : planned != null ? planned
                    : t.getCreatedAt() != null ? t.getCreatedAt().toLocalDate() : null;
        }
        return new TaskInput(planned, done);
    }
}
