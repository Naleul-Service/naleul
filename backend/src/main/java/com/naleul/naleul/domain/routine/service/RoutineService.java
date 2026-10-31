package com.naleul.naleul.domain.routine.service;

import com.naleul.naleul.global.exception.CustomException;

import com.naleul.naleul.global.common.response.ErrorCode;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.service.DefaultBucketResolver;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.routine.deleter.RoutineCascadeDeleter;
import com.naleul.naleul.domain.routine.dto.request.RoutineCompleteRequest;
import com.naleul.naleul.domain.routine.dto.request.RoutineCreateRequest;
import com.naleul.naleul.domain.routine.dto.request.RoutineUpdateRequest;
import com.naleul.naleul.domain.routine.dto.response.RoutineResponse;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.factory.RoutineFactory;
import com.naleul.naleul.domain.routine.factory.RoutineTaskFactory;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.routine.validator.RoutineValidator;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class RoutineService {

    /** "오늘" 기준 — EC2 기본 시간대(UTC)가 아니라 한국 날짜 */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RoutineRepository routineRepository;
    private final CategoryFinder categoryFinder;
    private final UserFinder userFinder;
    private final RoutineValidator routineValidator;
    private final RoutineTaskFactory routineTaskFactory;
    private final RoutineFinder routineFinder;
    private final RoutineFactory routineFactory;
    private final RoutineCascadeDeleter routineCascadeDeleter;
    private final TaskRepository taskRepository;
    private final DefaultBucketResolver defaultBucketResolver;

    public RoutineResponse create(RoutineCreateRequest request, Long userId) {
        User user = userFinder.findUser(userId);
        GoalCategory goalCategory = categoryFinder.findGoalCategory(request.getGoalCategoryId(), user);
        // 기본 생활 습관 그릇(V52)에는 루틴을 만들지 않는다 — 습관은 /api/v1/habits 로 (Task 를 미리 만들지 않는 체크 전용)
        if (goalCategory.isHabit()) throw new CustomException(ErrorCode.HABIT_HOME_USE_HABITS);
        // 영역을 안 골랐으면 "기타 할 일"로 (트리 구조를 몰라도 루틴을 바로 추가할 수 있게)
        GeneralCategory generalCategory = request.getGeneralCategoryId() != null || defaultBucketResolver == null
                ? categoryFinder.findGeneralCategory(request.getGeneralCategoryId(), user)
                : defaultBucketResolver.bucketOf(goalCategory);

        routineValidator.validateCreate(request, user, generalCategory);

        Routine routine = routineFactory.create(user, goalCategory, generalCategory, request);
        routine.updateDescription(request.getDescription());
        routine.applyDayTimes(request.getDayTimes());   // 요일별 시간 (없으면 모든 요일 기본 시간)
        Routine savedRoutine = routineRepository.save(routine);

        List<Task> tasks = routineTaskFactory.generateTasks(savedRoutine);
        taskRepository.saveAll(tasks);

        return RoutineResponse.from(savedRoutine);
    }

    public RoutineResponse complete(Long routineId, Long userId, RoutineCompleteRequest request) {
        User user = userFinder.findUser(userId);
        Routine routine = routineFinder.findByIdAndUser(routineId, user);
        routine.complete(request.getRoutineEndDate());
        return RoutineResponse.from(routine);
    }

    public RoutineResponse update(Long userId, Long routineId, RoutineUpdateRequest request) {
        User user = userFinder.findUser(userId);
        Routine routine = routineFinder.findByIdAndUser(routineId, user);

        routineValidator.validateUpdate(request, routine);

        GoalCategory goalCategory = request.getGoalCategoryId() != null
                ? categoryFinder.findGoalCategory(request.getGoalCategoryId(), user)
                : null;
        GeneralCategory generalCategory = request.getGeneralCategoryId() != null
                ? categoryFinder.findGeneralCategory(request.getGeneralCategoryId(), user)
                : null;

        if (request.getRepeatDays() != null) {
            routine.updateRepeatDays(request.getRepeatDays());
        }
        // 요일별 시간 — 요일을 바꾼 뒤에 적용해야 새로 추가된 요일에도 들어간다
        if (request.getDayTimes() != null) {
            routine.applyDayTimes(request.getDayTimes());
        }

        routine.update(goalCategory, generalCategory,
                request.getRoutineName(),
                request.getRepeatStartDate(), request.getRepeatEndDate(),
                request.getRepeatStartTime(), request.getRepeatEndTime(),
                request.getNotificationEnabled());
        if (request.getDescription() != null) {
            routine.updateDescription(request.getDescription());
        }

        syncTasksWithRoutine(routine);
        return RoutineResponse.from(routine);
    }

    /**
     * GeneralCategory가 다른 GoalCategory로 재배정될 때,
     * 그 하위 Routine과 Task의 goalCategory 참조도 함께 갱신한다.
     */
    public void syncGoalCategoryForGeneralCategory(Long generalCategoryId, GoalCategory newGoalCategory) {
        List<Routine> routines = routineRepository.findAllActiveByGeneralCategoryId(generalCategoryId);
        for (Routine routine : routines) {
            routine.reassignGoalCategory(newGoalCategory);
            taskRepository.findByRoutine(routine)
                    .forEach(task -> task.reassignGoalCategory(newGoalCategory));
        }
    }

    public void delete(Long routineId, Long userId) {
        User user = userFinder.findUser(userId);
        Routine routine = routineFinder.findByIdAndUser(routineId, user);
        routineCascadeDeleter.deleteRoutineAndTasks(routine);
    }

    /**
     * 특정 GeneralCategory 하위의 모든 활성 Routine을 소프트 삭제하고,
     * 각 Routine의 미완료 Task도 함께 정리한다. (완료된 Task는 보존)
     * GeneralCategory/GoalCategory가 삭제될 때 상위 서비스에서 호출한다.
     */
    public void deleteAllByGeneralCategoryId(Long generalCategoryId) {
        List<Routine> routines = routineRepository.findAllActiveByGeneralCategoryId(generalCategoryId);
        routines.forEach(routineCascadeDeleter::deleteRoutineAndTasks);
    }

    /**
     * 루틴만 멈춘다 (소프트 삭제). Task 는 건드리지 않는다.
     * 목표 전체 삭제처럼 "지금 이전 Task 는 남기고 이후만 지우는" 정리를 호출하는 쪽이 따로 할 때 쓴다.
     */
    public void stopAllByGeneralCategoryId(Long generalCategoryId) {
        routineRepository.findAllActiveByGeneralCategoryId(generalCategoryId).forEach(Routine::delete);
    }

    // ── private 헬퍼 (update()가 사용) ──────────────────────

    /**
     * 루틴의 현재 상태(기간/요일/시간/이름)를 기준으로 하위 Task를 동기화한다.
     * - 완료된(COMPLETED) Task: 절대 건드리지 않음 (과거 실행 기록 보존)
     * - 오늘 이전 날짜의 Task: 완료하지 않았어도 그대로 둔다 (놓친 기록 보존 — 나의 패턴 실행률 계산에 필요)
     * - 오늘 이후의 완료되지 않은 Task: 삭제 후, 새 조건으로 오늘부터 재생성
     * - repeatDays가 비어있으면 "매일 반복"으로 취급
     *
     * ⚠️ 예전에는 지난 날짜의 미완료 Task 까지 지우고 다시 만들었다.
     *    그래서 루틴 시간을 한 번 바꾸면 그동안 놓친 기록이 사라지고 실행률이 실제보다 높게 나왔다. (명세 4 · 4-3)
     */
    private void syncTasksWithRoutine(Routine routine) {
        syncTasksWithRoutine(routine, LocalDate.now(KST));
    }

    void syncTasksWithRoutine(Routine routine, LocalDate today) {
        if (routine.getRepeatStartDate() == null || routine.getRepeatEndDate() == null) {
            return;
        }

        List<Task> existingTasks = taskRepository.findByRoutine(routine);

        List<Task> replaceable = existingTasks.stream()
                .filter(task -> task.getTaskStatus() != TaskStatus.COMPLETED)
                .filter(task -> task.getRoutineDate() == null || !task.getRoutineDate().isBefore(today))
                .toList();
        taskRepository.deleteAll(replaceable);

        Set<LocalDate> keptDates = existingTasks.stream()
                .filter(task -> !replaceable.contains(task))
                .map(Task::getRoutineDate)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Task> newTasks = routineTaskFactory.generateTasks(routine, keptDates, today);
        taskRepository.saveAll(newTasks);
    }
}