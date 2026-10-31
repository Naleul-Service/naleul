package com.naleul.naleul.domain.task.service;

import com.naleul.naleul.domain.actualTask.entity.TaskActual;
import com.naleul.naleul.domain.actualTask.repository.TaskActualRepository;
import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.pattern.service.TaskScheduleLogWriter;
import com.naleul.naleul.domain.task.dto.request.*;
import com.naleul.naleul.domain.task.dto.response.TaskMonthlyResponse;
import com.naleul.naleul.domain.task.dto.response.TaskPageResponse;
import com.naleul.naleul.domain.task.dto.response.TaskResponse;
import com.naleul.naleul.domain.task.dto.response.TaskWeeklyResponse;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskPriority;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.policy.TaskQuotaValidator;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.naleul.naleul.global.util.KstDateTimeUtils.*;
import static com.naleul.naleul.global.util.TaskCommonUtils.calculateMinutes;
import static com.naleul.naleul.global.util.TaskCommonUtils.parseSourceType;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskFinder taskFinder;
    private final UserFinder userFinder;
    private final CategoryFinder categoryFinder;
    private final TaskQuotaValidator taskQuotaValidator;
    private final TaskActualRepository taskActualRepository;
    private final com.naleul.naleul.domain.actualTask.repository.TaskActualSegmentRepository segmentRepository;
    /** 나의 패턴: 수정 화면에서 시간을 바꾼 기록 (명세 4 · 6-2) */
    private final TaskScheduleLogWriter scheduleLogWriter;

    @Transactional
    public TaskResponse createTask(Long userId, TaskCreateRequest request) {
        User user = userFinder.findUser(userId);
        GoalCategory goalCategory = categoryFinder.findGoalCategory(request.goalCategoryId(), user);
        GeneralCategory generalCategory = categoryFinder.findGeneralCategory(request.generalCategoryId(), user);

        LocalDate targetDate = request.plannedStartAt() != null
                ? toDate(request.plannedStartAt())
                : toDate(LocalDateTime.now());
        taskQuotaValidator.validateDailyManualQuota(user, targetDate);

        boolean effectiveNotificationEnabled = request.notificationEnabled() == null || request.notificationEnabled();
        taskQuotaValidator.validatePushNotificationAccess(user, effectiveNotificationEnabled);

        Task task = Task.builder()
                .taskName(request.taskName())
                .taskPriority(request.taskPriority())
                .taskStatus(TaskStatus.TODO)
                .user(user)
                .goalCategory(goalCategory)
                .generalCategory(generalCategory)
                .plannedStartAt(request.plannedStartAt())
                .plannedEndAt(request.plannedEndAt())
                .plannedDurationMinutes(calculateMinutes(request.plannedStartAt(), request.plannedEndAt()))
                .defaultSettingStatus(request.defaultSettingStatus())
                .notificationEnabled(effectiveNotificationEnabled)
                .build();

        taskRepository.save(task);
        return TaskResponse.from(task);
    }

    public TaskResponse getTask(Long userId, Long taskId) {
        return TaskResponse.from(taskFinder.findTaskWithDetails(taskId, userId));
    }


    public List<TaskResponse> getTasksByUser(Long userId) {
        return taskRepository.findAllByUserIdWithDetails(userId)
                .stream()
                .map(TaskResponse::from)
                .toList();
    }

    public TaskPageResponse getTasksByTimeRange(Long userId, TaskTimeRangeRequest request, Pageable pageable) {
        if (request.startTime().isAfter(request.endTime())) {
            throw new CustomException(ErrorCode.TASK_INVALID_TIME_RANGE);
        }

        LocalDateTime startDateTime = LocalDateTime.of(request.date(), request.startTime());
        LocalDateTime endDateTime = LocalDateTime.of(request.date(), request.endTime());

        Page<Task> taskPage = taskRepository.findByTimeRange(userId, startDateTime, endDateTime, pageable);
        return TaskPageResponse.from(taskPage.map(TaskResponse::from));
    }

    public List<TaskResponse> getDailyTasks(Long userId, TaskDailyRequest request) {
        TaskPriority priority = parsePriority(request.priority());
        String sourceType = parseSourceType(request.sourceType());

        LocalDateTime kstDayStart = dayStart(request.date());
        LocalDateTime kstDayEnd = dayEnd(request.date());

        return taskRepository.findDailyTasks(
                        userId,
                        request.date(),
                        kstDayStart,
                        kstDayEnd,
                        request.goalCategoryId(),
                        request.generalCategoryId(),
                        priority,
                        sourceType
                )
                .stream()
                .map(TaskResponse::from)
                .toList();
    }

    public TaskWeeklyResponse getWeeklyTasks(Long userId, TaskWeeklyRequest request) {
        validateWeekRange(request);

        TaskPriority priority = parsePriority(request.priority());
        String sourceType = parseSourceType(request.sourceType());

        List<Task> tasks = taskRepository.findWeeklyTasksWithoutPage(
                userId,
                request.startDate() != null ? request.startDate().minusDays(1) : null,
                request.endDate() != null ? request.endDate().plusDays(1) : null,
                request.goalCategoryId(),
                request.generalCategoryId(),
                priority,
                sourceType
        );

        Map<String, List<TaskResponse>> tasksByDay = WeeklyTaskGrouper.groupByDay(tasks, request.startDate());

        return TaskWeeklyResponse.from(tasksByDay);
    }

    public TaskMonthlyResponse getMonthlyTasks(Long userId, TaskMonthlyRequest request) {
        String sourceType = parseSourceType(request.sourceType());

        List<Task> tasks = taskRepository.findMonthlyTasksWithoutPage(
                userId,
                request.year(),
                request.month(),
                sourceType
        );

        LocalDate firstDay = LocalDate.of(request.year(), request.month(), 1);
        LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        Map<String, List<TaskResponse>> tasksByDate = new LinkedHashMap<>();
        firstDay.datesUntil(lastDay.plusDays(1))
                .forEach(date -> tasksByDate.put(date.toString(), new ArrayList<>()));

        tasks.forEach(task -> {
            if (task.getPlannedStartAt() == null) {
                if (task.getRoutineDate() != null) {
                    putIfPresent(tasksByDate, task.getRoutineDate(), TaskResponse.from(task));
                }
                return;
            }

            LocalDate startDate = toDate(task.getPlannedStartAt());
            LocalDate endDate = task.getPlannedEndAt() != null
                    ? toDate(task.getPlannedEndAt())
                    : startDate;

            putIfPresent(tasksByDate, startDate, TaskResponse.from(task));
            if (!endDate.equals(startDate)) {
                putIfPresent(tasksByDate, endDate, TaskResponse.from(task));
            }
        });

        return TaskMonthlyResponse.from(tasksByDate);
    }

    @Transactional
    public TaskResponse updateTask(Long userId, Long taskId, TaskUpdateRequest request) {
        Task task = taskFinder.findTaskWithDetails(taskId, userId);
        User user = task.getUser();

        GoalCategory goalCategory = categoryFinder.findGoalCategory(request.goalCategoryId(), user);
        GeneralCategory generalCategory = categoryFinder.findGeneralCategory(request.generalCategoryId(), user);


        boolean notificationEnabled = request.notificationEnabled() != null
                ? request.notificationEnabled()
                : task.isNotificationEnabled();

        taskQuotaValidator.validatePushNotificationAccess(user, notificationEnabled);

        LocalDate fromDate = TaskScheduleLogWriter.dateOf(task);
        LocalDateTime fromStart = task.getPlannedStartAt();

        task.update(
                request.taskName(),
                request.taskPriority(),
                goalCategory,
                generalCategory,
                request.plannedStartAt(),
                request.plannedEndAt(),
                request.defaultSettingStatus(),
                notificationEnabled
        );

        // 미션 Task 는 패턴 분석에서 빠지므로 기록하지 않는다
        if (!task.isMissionTask()) {
            if (task.getPlannedStartAt() == null) {
                scheduleLogWriter.userUnscheduled(userId, task, fromDate, fromStart);
            } else {
                scheduleLogWriter.userMoved(userId, task, fromDate, fromStart);
            }
        }

        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse updateMissionTaskSchedule(Long userId, Long taskId, MissionTaskScheduleRequest request) {
        Task task = taskFinder.findTaskWithDetails(taskId, userId);

        // 미션 Task가 아니면 이 엔드포인트로 접근 못 하게 방어
        if (task.getMissionParticipant() == null) {
            throw new CustomException(ErrorCode.TASK_NOT_MISSION_TYPE);
        }

        if (request.plannedStartAt() != null && request.plannedEndAt() != null
                && request.plannedStartAt().isAfter(request.plannedEndAt())) {
            throw new CustomException(ErrorCode.TASK_INVALID_TIME_RANGE);
        }

        task.updateSchedule(request.plannedStartAt(), request.plannedEndAt());
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse cancelCompleteTask(Long userId, Long taskId) {
        Task task = taskFinder.findTaskWithDetails(taskId, userId);

        if (task.getTaskStatus() != TaskStatus.COMPLETED) {
            throw new CustomException(ErrorCode.TASK_NOT_COMPLETED);
        }

        // 실제 기록 초기화 (나눠서 한 구간도 같이 지움)
        taskActualRepository.findAllByTaskTaskIdOrderByActualStartAtAsc(taskId).forEach(a -> {
            a.reset();
            if (a.getTaskActualId() != null) segmentRepository.deleteAllByTaskActualId(a.getTaskActualId());
        });

        task.cancelComplete();
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse completeTask(Long userId, Long taskId) {
        Task task = taskFinder.findTaskWithDetails(taskId, userId);
        task.complete();
        return TaskResponse.from(task);
    }

    @Transactional
    public void deleteTask(Long userId, Long taskId) {
        Task task = taskFinder.findTaskWithDetails(taskId, userId);
        taskRepository.delete(task);
    }

    // ── 기타 유틸 ─────────────────────────────────────────

    private void validateWeekRange(TaskWeeklyRequest request) {
        if (request.startDate() == null || request.endDate() == null) return;
        if (request.startDate().isAfter(request.endDate())) {
            throw new CustomException(ErrorCode.TASK_INVALID_DATE_RANGE);
        }
        if (request.startDate().plusDays(7).isBefore(request.endDate())) {
            throw new CustomException(ErrorCode.TASK_INVALID_WEEK_RANGE);
        }
    }

    private void putIfPresent(Map<String, List<TaskResponse>> map, LocalDate date, TaskResponse response) {
        String key = date.toString();
        if (map.containsKey(key)) {
            map.get(key).add(response);
        }
    }

    private TaskPriority parsePriority(String priority) {
        if (priority == null) return null;
        try {
            return TaskPriority.valueOf(priority.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_ENUM_VALUE);
        }
    }
}