package com.naleul.naleul.domain.goalOutcome.service;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalCreation.personal.RoutineLoad;
import com.naleul.naleul.domain.goalOutcome.dto.OutcomePromptResponse;
import com.naleul.naleul.domain.goalOutcome.entity.GoalOutcome;
import com.naleul.naleul.domain.goalOutcome.entity.OutcomeSnapshot;
import com.naleul.naleul.domain.goalOutcome.enums.OutcomePromptStatus;
import com.naleul.naleul.domain.goalOutcome.enums.OutcomeResult;
import com.naleul.naleul.domain.goalOutcome.enums.OutcomeSource;
import com.naleul.naleul.domain.goalOutcome.repository.GoalOutcomeRepository;
import com.naleul.naleul.domain.goalOutcome.service.GoalOutcomeCalculator.MetricLogs;
import com.naleul.naleul.domain.goalOutcome.service.GoalOutcomeCalculator.RoutineDue;
import com.naleul.naleul.domain.goalOutcome.service.GoalOutcomeCalculator.TaskDue;
import com.naleul.naleul.domain.goalProgress.entity.GoalMetricLog;
import com.naleul.naleul.domain.goalProgress.repository.GoalMetricLogRepository;
import com.naleul.naleul.domain.planner.repository.TimetableQueryRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 목표 결과 기록 (V47 goal_outcome).
 *  - 사용자가 "목표 마무리"를 누를 때: 고른 결과(안 골랐으면 자동 판정)와 스냅샷
 *  - 기간이 끝나 자동 완료될 때(자정 스케줄러): 자동 판정
 *  - 끝난 뒤 "이 목표 어땠나요?"에서 다시 고를 때: 결과만 바꾼다
 * 업무형(기록형)·임시 목표는 "달성"이라는 개념이 없어 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GoalOutcomeRecorder {

    /** 결과 확인 모달은 끝난 지 이만큼 안 된 목표만 묻는다 */
    static final int PROMPT_WINDOW_DAYS = 30;

    private final GoalOutcomeRepository outcomeRepository;
    private final TimetableQueryRepository taskQueryRepository;
    private final RoutineRepository routineRepository;
    private final CategoryFinder categoryFinder;
    private final UserFinder userFinder;
    private final PlannerClock clock;
    private final GoalMetricLogRepository metricLogRepository;

    /** @param chosen 사용자가 고른 결과 (null 이면 자동 판정) */
    public Optional<GoalOutcome> record(GoalCategory goal, OutcomeResult chosen, OutcomeSource source) {
        return record(goal, chosen, source, null);
    }

    /**
     * ⚠️ 목표 마무리(GoalCategory.complete)는 종료일을 "끝낸 날"로 덮어쓴다.
     *    원래 계획 기간을 남기려면 complete 를 부르기 "전에" 이걸 불러야 한다.
     * @param endedOn 실제로 끝낸 날 (null 이면 종료일과 오늘 중 이른 날)
     */
    public Optional<GoalOutcome> record(GoalCategory goal, OutcomeResult chosen, OutcomeSource source, LocalDate endedOn) {
        if (!goal.isAchievement() || goal.isTemporary()) return Optional.empty();
        User user = goal.getUser();
        LocalDate today = clock.today();
        if (endedOn == null) {
            endedOn = goal.getGoalCategoryEndDate() != null && goal.getGoalCategoryEndDate().isBefore(today)
                    ? goal.getGoalCategoryEndDate() : today;
        }
        final LocalDate end = endedOn;

        List<Task> tasks = taskQueryRepository.findAllOfGoal(user.getUserId(), goal.getGoalCategoryId());
        List<RoutineDue> routineDues = tasks.stream()
                .filter(t -> t.getRoutine() != null && t.getRoutineDate() != null)
                .filter(t -> !t.getRoutineDate().isAfter(end))
                .filter(t -> t.getTaskStatus() != TaskStatus.DELETED)
                .map(t -> new RoutineDue(t.getRoutine().getRoutineId(), t.getRoutineDate(),
                        t.getTaskStatus() == TaskStatus.COMPLETED))
                .toList();
        List<TaskDue> others = tasks.stream()
                .filter(t -> t.getRoutine() == null)
                .map(t -> new TaskDue(plannedDate(t), t.getTaskStatus() == TaskStatus.COMPLETED))
                .toList();
        Map<Long, Integer> weekly = new LinkedHashMap<>();
        for (Routine r : routineRepository.findAllByGoalCategoryId(goal.getGoalCategoryId())) {
            weekly.put(r.getRoutineId(), RoutineLoad.weeklyMinutes(r));
        }

        // 수치 기록 현황 (끝난 날까지) — 기록을 안 했는지 판단용
        List<LocalDate> metricLogDates = metricLogRepository.findAllOfGoal(goal.getGoalCategoryId()).stream()
                .map(GoalMetricLog::getLogDate)
                .filter(d -> !d.isAfter(end))
                .toList();
        MetricLogs metricLogs = new MetricLogs(metricLogDates.size(),
                metricLogDates.stream().max(Comparator.naturalOrder()).orElse(null));

        // 활동한 날 = Task 완료 버튼을 누른 날 + 수치를 기록한 날
        List<LocalDate> activityDates = new ArrayList<>(metricLogDates);
        tasks.stream()
                .filter(t -> t.getTaskStatus() == TaskStatus.COMPLETED && t.getCompletedAt() != null)
                .map(t -> t.getCompletedAt().toLocalDate())
                .forEach(activityDates::add);

        OutcomeSnapshot snapshot = GoalOutcomeCalculator.snapshot(
                goal.getGoalCategoryStartDate(), goal.getGoalCategoryEndDate(), endedOn,
                goal.getStartValue(), goal.getCurrentValue(), goal.getTargetValue(),
                routineDues, others, weekly, metricLogs, activityDates);
        OutcomeResult autoResult = GoalOutcomeCalculator.infer(snapshot);
        OutcomeResult result = chosen != null ? chosen : autoResult;

        GoalOutcome outcome = outcomeRepository.findByGoalCategory_GoalCategoryId(goal.getGoalCategoryId())
                .orElseGet(() -> GoalOutcome.create(goal, user));
        // 사용자가 이미 직접 고른 결과는 자동 판정으로 덮지 않는다
        if (source == OutcomeSource.AUTO && outcome.getSource() == OutcomeSource.USER) return Optional.of(outcome);
        outcome.fill(result, autoResult, source, goal.getGoalType(), goal.getGoalSubType(), snapshot, goal.isAiGenerated());
        return Optional.of(outcomeRepository.save(outcome));
    }

    /** 끝난 목표의 결과를 사용자가 다시 고른다 (자동 판정을 고치거나, 기록이 없던 옛 목표) */
    public GoalOutcome changeResult(Long userId, Long goalCategoryId, OutcomeResult result) {
        User user = userFinder.findUser(userId);
        GoalCategory goal = categoryFinder.findGoalCategory(goalCategoryId, user);
        if (goal.getGoalCategoryStatus() != GoalCategoryStatus.COMPLETED) {
            throw new CustomException(ErrorCode.GOAL_CATEGORY_NOT_COMPLETED);
        }
        return outcomeRepository.findByGoalCategory_GoalCategoryId(goalCategoryId)
                .map(o -> {
                    o.changeResult(result, LocalDateTime.now());   // created_at 과 같은 서버 시간대
                    return o;
                })
                .orElseGet(() -> record(goal, result, OutcomeSource.USER)
                        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT_VALUE)));
    }

    /**
     * 결과 확인 모달에 띄울 목표 — 기간이 끝나 자동 판정됐고 아직 묻지 않은 것 (최근 PROMPT_WINDOW_DAYS 일, 오래된 순).
     * 너무 오래된 목표는 기억이 흐려 정확한 답을 받기 어려워 묻지 않는다.
     */
    @Transactional(readOnly = true)
    public List<OutcomePromptResponse> pendingPrompts(Long userId) {
        return outcomeRepository.findPendingPrompts(userId, OutcomePromptStatus.PENDING,
                        LocalDateTime.now().minusDays(PROMPT_WINDOW_DAYS))   // created_at 은 서버 시간대
                .stream().map(OutcomePromptResponse::of).toList();   // 색상(지연 로딩)을 트랜잭션 안에서 읽는다
    }

    /** 결과 확인 모달 "나중에" — 자동 판정을 그대로 둔다 */
    public void dismissPrompt(Long userId, Long goalCategoryId) {
        GoalOutcome o = outcomeRepository.findByGoalCategory_GoalCategoryId(goalCategoryId)
                .filter(x -> x.getUser().getUserId().equals(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.GOAL_CATEGORY_NOT_FOUND));
        o.dismissPrompt(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Optional<GoalOutcome> find(Long goalCategoryId) {
        return outcomeRepository.findByGoalCategory_GoalCategoryId(goalCategoryId);
    }

    private static LocalDate plannedDate(Task t) {
        return t.getScheduledDate() != null ? t.getScheduledDate()
                : t.getPlannedStartAt() != null ? t.getPlannedStartAt().toLocalDate()
                : t.getDueDate();
    }
}
