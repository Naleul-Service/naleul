package com.naleul.naleul.domain.work.service;

import com.naleul.naleul.domain.actualTask.entity.TaskActual;
import com.naleul.naleul.domain.actualTask.repository.TaskActualRepository;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.service.DefaultBucketResolver;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.planner.service.TaskScheduleService;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.policy.TaskQuotaValidator;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.task.service.TaskFinder;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.domain.work.dto.request.WorkTaskBulkRequest;
import com.naleul.naleul.domain.work.dto.request.WorkTaskCreateRequest;
import com.naleul.naleul.domain.work.dto.request.WorkTaskUpdateRequest;
import com.naleul.naleul.domain.work.dto.response.WorkTaskItem;
import com.naleul.naleul.domain.work.entity.WorkTopic;
import com.naleul.naleul.domain.work.enums.WorkBoardColumn;
import com.naleul.naleul.domain.work.support.WorkTaskTextParser;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 업무형 보드의 Task 추가 · 일괄 추가 · 고치기 · 칸 옮기기 (V52). 삭제는 기존 DELETE /api/v1/tasks/{taskId}.
 *
 * Free 플랜의 "하루에 직접 만드는 Task 2개" 한도는 그대로 따른다 (생성일 기준, TaskQuotaValidator).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WorkTaskService {

    static final int BULK_MAX = 50;

    private final TaskRepository taskRepository;
    private final TaskFinder taskFinder;
    private final TaskActualRepository taskActualRepository;
    private final com.naleul.naleul.domain.actualTask.repository.TaskActualSegmentRepository segmentRepository;
    private final TaskScheduleService taskScheduleService;
    private final TaskQuotaValidator taskQuotaValidator;
    private final DefaultBucketResolver defaultBucketResolver;
    private final WorkGoalFinder workGoalFinder;
    private final WorkTopicService workTopicService;
    private final UserFinder userFinder;
    private final PlannerClock clock;

    public WorkTaskItem create(Long userId, Long goalCategoryId, WorkTaskCreateRequest request) {
        User user = userFinder.findUser(userId);
        GoalCategory goal = workGoalFinder.find(goalCategoryId, user);
        WorkTopic topic = topicOf(goalCategoryId, request.topicId());
        taskQuotaValidator.validateCanCreate(user, 1, null);

        Task task = Task.createWorkTask(user, goal, defaultBucketResolver.bucketOf(goal), topic,
                request.taskName().strip(), request.date(), request.dueDate(), request.memo());
        taskRepository.save(task);
        if (request.status() != null) move(task, request.status());
        return WorkTaskItem.from(task, clock.today());
    }

    /** 여러 줄 한 번에 — 같은 주제 · 날짜 · 마감일로 */
    public List<WorkTaskItem> createBulk(Long userId, Long goalCategoryId, WorkTaskBulkRequest request) {
        User user = userFinder.findUser(userId);
        GoalCategory goal = workGoalFinder.find(goalCategoryId, user);
        List<String> names = WorkTaskTextParser.parse(request.text(), request.lines());
        if (names.isEmpty() || names.size() > BULK_MAX) throw new CustomException(ErrorCode.WORK_TASK_BULK_INVALID);
        WorkTopic topic = topicOf(goalCategoryId, request.topicId());
        taskQuotaValidator.validateCanCreate(user, names.size(), null);

        GeneralCategory bucket = defaultBucketResolver.bucketOf(goal);
        List<Task> tasks = new ArrayList<>();
        for (String name : names) {
            tasks.add(Task.createWorkTask(user, goal, bucket, topic, name, request.date(), request.dueDate(), null));
        }
        taskRepository.saveAll(tasks);
        LocalDate today = clock.today();
        return tasks.stream().map(t -> WorkTaskItem.from(t, today)).toList();
    }

    public WorkTaskItem update(Long userId, Long goalCategoryId, Long taskId, WorkTaskUpdateRequest request) {
        User user = userFinder.findUser(userId);
        workGoalFinder.find(goalCategoryId, user);
        Task task = findEditable(userId, goalCategoryId, taskId);

        WorkTopic topic = Boolean.TRUE.equals(request.clearTopic()) ? null : topicOf(goalCategoryId, request.topicId());
        task.editWork(request.taskName(), topic, Boolean.TRUE.equals(request.clearTopic()),
                request.dueDate(), Boolean.TRUE.equals(request.clearDueDate()), request.memo());

        if (Boolean.TRUE.equals(request.clearDate())) {
            if (task.isPlaced()) taskScheduleService.unschedule(userId, taskId);
            task.moveToBacklog();
        } else if (request.date() != null && !Objects.equals(request.date(), task.plannedDate())) {
            if (task.isPlaced()) taskScheduleService.unschedule(userId, taskId);
            task.moveUnscheduledTo(request.date(), null);
        }
        if (request.status() != null) move(task, request.status());
        return WorkTaskItem.from(task, clock.today());
    }

    public WorkTaskItem changeStatus(Long userId, Long goalCategoryId, Long taskId, WorkBoardColumn status) {
        workGoalFinder.find(goalCategoryId, userFinder.findUser(userId));
        Task task = findEditable(userId, goalCategoryId, taskId);
        move(task, status);
        return WorkTaskItem.from(task, clock.today());
    }

    // ── 내부 ─────────────────────────────────────────────

    /** 칸 옮기기. 완료 ↔ 미완료는 기존 완료 · 완료 취소와 똑같이 (완료 취소면 실제 시간 기록도 초기화) */
    void move(Task task, WorkBoardColumn to) {
        if (to == WorkBoardColumn.DONE) {
            if (task.getTaskStatus() != TaskStatus.COMPLETED) task.complete();
            return;
        }
        if (task.getTaskStatus() == TaskStatus.COMPLETED) {
            taskActualRepository.findAllByTaskTaskIdOrderByActualStartAtAsc(task.getTaskId()).forEach(a -> {
                a.reset();
                if (a.getTaskActualId() != null) segmentRepository.deleteAllByTaskActualId(a.getTaskActualId());
            });
            task.cancelComplete();
        }
        task.markWorkStatus(to);
    }

    private Task findEditable(Long userId, Long goalCategoryId, Long taskId) {
        Task task = taskFinder.findTaskWithDetails(taskId, userId);
        if (task.getGoalCategory() == null || !goalCategoryId.equals(task.getGoalCategory().getGoalCategoryId())
                || task.getTaskStatus() == TaskStatus.DELETED) {
            throw new CustomException(ErrorCode.WORK_TASK_NOT_FOUND);
        }
        if (!task.isOneOff()) throw new CustomException(ErrorCode.WORK_TASK_NOT_EDITABLE);
        return task;
    }

    private WorkTopic topicOf(Long goalCategoryId, Long topicId) {
        return topicId == null ? null : workTopicService.find(goalCategoryId, topicId);
    }
}
