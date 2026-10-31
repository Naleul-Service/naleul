package com.naleul.naleul.domain.actualTask.service;

import com.naleul.naleul.domain.actualTask.dto.request.*;
import com.naleul.naleul.domain.actualTask.dto.response.TaskActualResponse;
import com.naleul.naleul.domain.actualTask.dto.response.TaskActualWeeklyResponse;
import com.naleul.naleul.domain.actualTask.entity.TaskActual;
import com.naleul.naleul.domain.actualTask.entity.TaskActualSegment;
import com.naleul.naleul.domain.actualTask.repository.TaskActualSegmentRepository;
import com.naleul.naleul.domain.actualTask.repository.TaskActualRepository;
import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.task.dto.response.TaskResponse;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskSourceType;
import com.naleul.naleul.domain.task.policy.TaskQuotaValidator;
import com.naleul.naleul.domain.task.service.TaskFinder;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
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
public class TaskActualService {

    private final TaskActualRepository taskActualRepository;
    private final TaskActualFinder taskActualFinder;
    private final UserFinder userFinder;
    private final CategoryFinder categoryFinder;
    private final TaskFinder taskFinder;
    private final TaskQuotaValidator taskQuotaValidator;
    private final TaskActualSegmentRepository segmentRepository;

    @Transactional
    public TaskActualResponse createActual(Long userId, TaskActualCreateRequest request) {
        User user = userFinder.findUser(userId);
        GoalCategory goalCategory = categoryFinder.findGoalCategory(request.goalCategoryId(), user);
        GeneralCategory generalCategory = categoryFinder.findGeneralCategory(request.generalCategoryId(), user);

        Task task = null;
        if (request.taskId() != null) {
            task = taskFinder.findTaskWithDetails(request.taskId(), userId);
        } else {
            LocalDate targetDate = toDate(
                    request.actualStartAt() != null ? request.actualStartAt() : LocalDateTime.now()
            );
            taskQuotaValidator.validateDailyManualQuota(user, targetDate);
        }

        TaskActual actual = TaskActual.builder()
                .task(task)
                .user(user)
                .goalCategory(goalCategory)
                .generalCategory(generalCategory)
                .taskName(request.taskName())
                .actualStartAt(request.actualStartAt())
                .actualEndAt(request.actualEndAt())
                .actualDurationMinutes(calculateMinutes(request.actualStartAt(), request.actualEndAt()))
                .build();

        return TaskActualResponse.from(taskActualRepository.save(actual));
    }

    /** 여러 번 나눠서 한 경우 최대 구간 수 */
    static final int MAX_SEGMENTS = 10;

    @Transactional
    public TaskResponse recordActual(Long userId, Long taskId, TaskUpdateActualRequest request) {
        Task task = taskFinder.findTaskWithDetails(taskId, userId);

        TaskActual actual = taskActualRepository
                .findFirstByTaskTaskId(taskId)
                .orElse(TaskActual.builder()
                        .task(task)
                        .user(task.getUser())
                        .goalCategory(task.getGoalCategory())
                        .generalCategory(task.getGeneralCategory())
                        .taskName(task.getTaskName())
                        .build());

        if (request.segments() != null && request.segments().size() > 1) {
            // 여러 번 나눠서 했어요: task_actual 은 한 줄(첫 시작 ~ 마지막 끝, 소요 = 합계), 구간은 따로
            List<TaskUpdateActualRequest.Segment> segs = validSegments(request.segments());
            long worked = segs.stream()
                    .mapToLong(g -> java.time.Duration.between(g.start(), g.end()).toMinutes()).sum();
            actual.updateSpan(segs.get(0).start(), segs.get(segs.size() - 1).end(), worked);
            TaskActual saved = taskActualRepository.save(actual);
            segmentRepository.deleteAllByTaskActualId(saved.getTaskActualId());
            for (TaskUpdateActualRequest.Segment g : segs) {
                segmentRepository.save(TaskActualSegment.of(saved.getTaskActualId(), g.start(), g.end()));
            }
            task.complete();
            return TaskResponse.from(task);
        }

        if (request.segments() != null && request.segments().size() == 1) {
            var only = validSegments(request.segments()).get(0);
            request = new TaskUpdateActualRequest(only.start(), only.end());
        }
        if (actual.getTaskActualId() != null) segmentRepository.deleteAllByTaskActualId(actual.getTaskActualId());
        actual.update(request.actualStartAt(), request.actualEndAt());
        taskActualRepository.save(actual);

        task.complete();

        return TaskResponse.from(task);
    }

    /** 끝이 시작보다 뒤 · 서로 겹치지 않음 · 최대 10개. 시작 순으로 정렬해 돌려준다 */
    static List<TaskUpdateActualRequest.Segment> validSegments(List<TaskUpdateActualRequest.Segment> raw) {
        if (raw.size() > MAX_SEGMENTS) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        List<TaskUpdateActualRequest.Segment> segs = new java.util.ArrayList<>(raw);
        for (TaskUpdateActualRequest.Segment s : segs) {
            if (s == null || s.start() == null || s.end() == null || !s.end().isAfter(s.start())) {
                throw new CustomException(ErrorCode.TASK_ACTUAL_INVALID_SEGMENT);
            }
        }
        segs.sort(java.util.Comparator.comparing(TaskUpdateActualRequest.Segment::start));
        for (int i = 1; i < segs.size(); i++) {
            if (segs.get(i).start().isBefore(segs.get(i - 1).end())) {
                throw new CustomException(ErrorCode.TASK_ACTUAL_INVALID_SEGMENT);
            }
        }
        return segs;
    }

    public List<TaskActualResponse> getDailyActuals(Long userId, TaskActualDailyRequest request) {
        String sourceType = parseSourceType(request.sourceType());
        LocalDateTime kstDayStart = dayStart(request.date());
        LocalDateTime kstDayEnd = dayEnd(request.date());

        return taskActualRepository.findDailyActuals(
                        userId,
                        kstDayStart,
                        kstDayEnd,
                        request.goalCategoryId(),
                        request.generalCategoryId(),
                        sourceType
                )
                .stream()
                .map(TaskActualResponse::from)
                .toList();
    }

    @Transactional
    public TaskActualResponse updateActual(Long userId, Long taskActualId, TaskActualUpdateRequest request) {
        TaskActual actual = taskActualFinder.findActualWithDetails(taskActualId, userId);
        User user = actual.getUser();
        GoalCategory goalCategory = categoryFinder.findGoalCategory(request.goalCategoryId(), user);
        GeneralCategory generalCategory = categoryFinder.findGeneralCategory(request.generalCategoryId(), user);

        actual.updateAll(
                request.taskName(),
                goalCategory,
                generalCategory,
                request.actualStartAt(),
                request.actualEndAt()
        );

        return TaskActualResponse.from(actual);
    }

    @Transactional
    public void deleteActual(Long userId, Long taskActualId) {
        TaskActual actual = taskActualFinder.findActualWithDetails(taskActualId, userId);
        taskActualRepository.delete(actual);
    }

    public TaskActualWeeklyResponse getWeeklyActuals(Long userId, TaskActualWeeklyRequest request) {
        String sourceType = parseSourceType(request.sourceType());

        LocalDateTime kstWeekStart = dayStart(request.startDate());
        LocalDateTime kstWeekEnd   = dayEnd(request.endDate());

        List<TaskActual> actuals = taskActualRepository.findWeeklyActuals(
                userId,
                kstWeekStart,
                kstWeekEnd,
                request.goalCategoryId(),
                request.generalCategoryId(),
                sourceType
        );

        Map<String, List<TaskActualResponse>> actualsByDay = new LinkedHashMap<>();
        for (String day : List.of("MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY")) {
            actualsByDay.put(day, new ArrayList<>());
        }

        for (TaskActual actual : actuals) {
            LocalDateTime kstStart = actual.getActualStartAt();
            LocalDateTime kstEnd   = actual.getActualEndAt();

            LocalDate startDate = kstStart.toLocalDate();
            LocalDate endDate   = kstEnd.toLocalDate();

            boolean endIsExact = kstEnd.toLocalTime().equals(LocalTime.MIDNIGHT);
            LocalDate effectiveEndDate = endIsExact ? endDate.minusDays(1) : endDate;

            LocalDate cursor = startDate;
            while (!cursor.isAfter(effectiveEndDate)) {
                if (!cursor.isBefore(request.startDate()) && !cursor.isAfter(request.endDate())) {
                    String dayKey = cursor.getDayOfWeek().name();
                    actualsByDay.get(dayKey).add(TaskActualResponse.from(actual));
                }
                cursor = cursor.plusDays(1);
            }
        }

        return TaskActualWeeklyResponse.from(actualsByDay);
    }
}