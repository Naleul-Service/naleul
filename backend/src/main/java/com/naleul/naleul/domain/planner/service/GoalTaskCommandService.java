package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.service.DefaultBucketResolver;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.milestone.entity.Milestone;
import com.naleul.naleul.domain.milestone.repository.MilestoneRepository;
import com.naleul.naleul.domain.planner.dto.request.GoalTaskRequest;
import com.naleul.naleul.domain.planner.dto.request.TaskScheduleRequest;
import com.naleul.naleul.domain.planner.dto.response.TimeBlockTaskResponse;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.policy.TaskQuotaValidator;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.task.service.TaskFinder;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 목표 상세에서 Task 직접 추가 · 수정 (삭제는 기존 DELETE /api/v1/tasks/{taskId}).
 *
 * 기존 PUT /api/v1/tasks/{taskId} 를 쓰지 않는 이유
 *  - 시간 없이 저장하면 plannedDurationMinutes 를 지워 AI Task 의 소요 시간이 사라진다
 *  - "날짜만 있고 시간 미정" Task 를 만들 수 없다 (routineDate 를 받지 않음)
 * 시간을 정하는 부분은 TaskScheduleService 를 그대로 써서 캘린더 드래그와 같은 규칙(겹치면 409, 자동 배치 블록은 밀어냄)을 따른다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GoalTaskCommandService {

    private final TaskRepository taskRepository;
    private final TaskFinder taskFinder;
    private final MilestoneRepository milestoneRepository;
    private final CategoryFinder categoryFinder;
    private final UserFinder userFinder;
    private final TaskQuotaValidator taskQuotaValidator;
    private final TaskScheduleService taskScheduleService;
    private final PlannerClock clock;
    private final DefaultBucketResolver defaultBucketResolver;

    public TimeBlockTaskResponse create(Long userId, Long goalCategoryId, GoalTaskRequest request) {
        User user = userFinder.findUser(userId);
        GoalCategory goal = categoryFinder.findGoalCategory(goalCategoryId, user);
        // 기본 생활 습관 그릇(V52)에도 일회성 Task(병원 가기 · 친구 만나기)는 만들 수 있다.
        // 반복되는 건 루틴 대신 습관(/api/v1/habits)으로 — 루틴은 RoutineService 에서 막는다
        GeneralCategory general = generalOfGoal(request.generalCategoryId(), user, goal);
        Milestone milestone = milestoneOfGoal(request.milestoneId(), userId, goal);
        validateTime(request);
        taskQuotaValidator.validateDailyManualQuota(user, request.date());

        Task task = Task.createForGoal(user, goal, general, milestone, request.taskName().strip(),
                blankToNull(request.emoji()), request.date(), request.durationMinutes(), request.dueDate());
        taskRepository.save(task);

        if (request.hasTime()) {
            // 겹치면 409 → 트랜잭션 롤백으로 Task 도 만들어지지 않는다
            taskScheduleService.reschedule(userId, task.getTaskId(), toSchedule(request), false);
        }
        return TimeBlockTaskResponse.from(task, clock.now());
    }

    public TimeBlockTaskResponse update(Long userId, Long goalCategoryId, Long taskId, GoalTaskRequest request) {
        User user = userFinder.findUser(userId);
        GoalCategory goal = categoryFinder.findGoalCategory(goalCategoryId, user);
        Task task = taskFinder.findTaskWithDetails(taskId, userId);
        if (task.getGoalCategory() == null || !goal.getGoalCategoryId().equals(task.getGoalCategory().getGoalCategoryId())) {
            throw new CustomException(ErrorCode.GOAL_TASK_NOT_FOUND);
        }
        if (!task.isOneOff()) {
            throw new CustomException(ErrorCode.GOAL_TASK_NOT_EDITABLE);
        }
        GeneralCategory general = generalOfGoal(request.generalCategoryId(), user, goal);
        Milestone milestone = milestoneOfGoal(request.milestoneId(), userId, goal);
        validateTime(request);

        task.editFromGoal(request.taskName().strip(), blankToNull(request.emoji()), general, milestone, request.dueDate());

        if (request.hasTime()) {
            TaskScheduleRequest target = toSchedule(request);
            boolean changed = !Objects.equals(task.getPlannedStartAt(), target.plannedStartAt())
                    || !Objects.equals(task.getPlannedEndAt(), target.plannedEndAt());
            if (changed) taskScheduleService.reschedule(userId, taskId, target, true);
        } else {
            if (task.isPlaced()) taskScheduleService.unschedule(userId, taskId);
            task.moveUnscheduledTo(request.date(), request.durationMinutes());
        }
        return TimeBlockTaskResponse.from(task, clock.now());
    }

    // ── 내부 ─────────────────────────────────────────────

    /** 이 목표의 세부 목표인지 확인. 안 골랐으면 목표의 "기타 할 일" */
    private GeneralCategory generalOfGoal(Long generalCategoryId, User user, GoalCategory goal) {
        if (generalCategoryId == null && defaultBucketResolver != null) return defaultBucketResolver.bucketOf(goal);
        GeneralCategory general = categoryFinder.findGeneralCategory(generalCategoryId, user);
        if (general.getGoalCategory() == null
                || !goal.getGoalCategoryId().equals(general.getGoalCategory().getGoalCategoryId())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return general;
    }

    /** 이 목표의 마일스톤인지 확인 (없으면 null) */
    private Milestone milestoneOfGoal(Long milestoneId, Long userId, GoalCategory goal) {
        if (milestoneId == null) return null;
        Milestone m = milestoneRepository.findOwned(milestoneId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.MILESTONE_NOT_FOUND));
        if (!goal.getGoalCategoryId().equals(m.getGoalCategory().getGoalCategoryId())) {
            throw new CustomException(ErrorCode.MILESTONE_NOT_FOUND);
        }
        return m;
    }

    private static void validateTime(GoalTaskRequest r) {
        if ((r.startTime() == null) != (r.endTime() == null) || (r.hasTime() && r.startTime().equals(r.endTime()))) {
            throw new CustomException(ErrorCode.GOAL_TASK_INVALID_TIME);
        }
    }

    /** date + startTime ~ endTime (종료가 시작보다 이르면 다음 날) */
    private static TaskScheduleRequest toSchedule(GoalTaskRequest r) {
        LocalDateTime start = r.date().atTime(r.startTime());
        LocalDateTime end = r.endTime().isAfter(r.startTime())
                ? r.date().atTime(r.endTime())
                : r.date().plusDays(1).atTime(r.endTime());
        return new TaskScheduleRequest(start, end);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
