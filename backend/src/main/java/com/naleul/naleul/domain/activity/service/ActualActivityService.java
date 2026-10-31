package com.naleul.naleul.domain.activity.service;

import com.naleul.naleul.domain.activity.dto.request.ActualActivityRequest;
import com.naleul.naleul.domain.activity.dto.response.ActualActivityResponse;
import com.naleul.naleul.domain.activity.entity.ActualActivity;
import com.naleul.naleul.domain.activity.repository.ActualActivityRepository;
import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.service.TaskFinder;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * 실제로 한 일 기록 (TimeTable 에 "실제" 블록으로 표시).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ActualActivityService {

    static final long MAX_MINUTES = 24 * 60;
    static final int MAX_RANGE_DAYS = 42;
    /** 시계 오차·입력 시간 고려: 지금으로부터 이 정도 뒤까지는 "지금"으로 본다 */
    static final long FUTURE_SLACK_MINUTES = 1;

    private final ActualActivityRepository repository;
    private final UserFinder userFinder;
    private final CategoryFinder categoryFinder;
    private final TaskFinder taskFinder;
    private final PlannerClock clock;

    @Transactional(readOnly = true)
    public List<ActualActivityResponse> getRange(Long userId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)
                || ChronoUnit.DAYS.between(startDate, endDate) >= MAX_RANGE_DAYS) {
            throw new CustomException(ErrorCode.ACTIVITY_INVALID_RANGE);
        }
        return repository.findOverlapping(userId, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay())
                .stream().map(ActualActivityResponse::from).toList();
    }

    public ActualActivityResponse create(Long userId, ActualActivityRequest request) {
        User user = userFinder.findUser(userId);
        validateTime(request.startAt(), request.endAt());
        validateNoOverlap(userId, null, request.startAt(), request.endAt());

        Task replaced = replacedTask(userId, request.replacedTaskId());
        ActualActivity activity = ActualActivity.create(user, request.title(), request.emoji(),
                request.startAt(), request.endAt(), goal(user, request.goalCategoryId()),
                replaced != null ? replaced.getTaskId() : null,
                replaced != null ? replaced.getTaskName() : null,
                request.memo());
        return ActualActivityResponse.from(repository.save(activity));
    }

    public ActualActivityResponse update(Long userId, Long activityId, ActualActivityRequest request) {
        User user = userFinder.findUser(userId);
        ActualActivity activity = findOwned(userId, activityId);
        validateTime(request.startAt(), request.endAt());
        validateNoOverlap(userId, activityId, request.startAt(), request.endAt());

        // 대신한 Task 를 바꾸지 않았으면 예전 스냅샷 유지 (그 Task 가 지워졌어도 이름은 남게)
        Long replacedId = request.replacedTaskId();
        String replacedName = activity.getReplacedTaskName();
        if (!Objects.equals(replacedId, activity.getReplacedTaskId())) {
            Task replaced = replacedTask(userId, replacedId);
            replacedId = replaced != null ? replaced.getTaskId() : null;
            replacedName = replaced != null ? replaced.getTaskName() : null;
        }
        activity.apply(request.title(), request.emoji(), request.startAt(), request.endAt(),
                goal(user, request.goalCategoryId()), replacedId, replacedName, request.memo());
        return ActualActivityResponse.from(activity);
    }

    public void delete(Long userId, Long activityId) {
        repository.delete(findOwned(userId, activityId));
    }

    // ── 검증 ──────────────────────────────────────────────

    void validateTime(LocalDateTime startAt, LocalDateTime endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new CustomException(ErrorCode.ACTIVITY_INVALID_TIME);
        }
        if (Duration.between(startAt, endAt).toMinutes() > MAX_MINUTES) {
            throw new CustomException(ErrorCode.ACTIVITY_TOO_LONG);
        }
        // 실제로 "한" 일이라 아직 오지 않은 시간은 안 된다
        if (endAt.isAfter(clock.now().plusMinutes(FUTURE_SLACK_MINUTES))) {
            throw new CustomException(ErrorCode.ACTIVITY_FUTURE);
        }
    }

    /** 같은 시간에 두 가지 일을 "실제로" 할 수는 없으니 기록끼리는 겹치지 않게 */
    private void validateNoOverlap(Long userId, Long selfId, LocalDateTime startAt, LocalDateTime endAt) {
        repository.findOverlapping(userId, startAt, endAt).stream()
                .filter(a -> !a.getActualActivityId().equals(selfId))
                .findFirst()
                .ifPresent(a -> {
                    throw new CustomException(ErrorCode.ACTIVITY_OVERLAP);
                });
    }

    private ActualActivity findOwned(Long userId, Long activityId) {
        return repository.findOwned(activityId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.ACTIVITY_NOT_FOUND));
    }

    private GoalCategory goal(User user, Long goalCategoryId) {
        return goalCategoryId == null ? null : categoryFinder.findGoalCategory(goalCategoryId, user);
    }

    private Task replacedTask(Long userId, Long taskId) {
        return taskId == null ? null : taskFinder.findTaskWithDetails(taskId, userId);
    }
}
