package com.naleul.naleul.domain.task.controller;

import com.naleul.naleul.domain.actualTask.dto.request.TaskUpdateActualRequest;
import com.naleul.naleul.domain.task.dto.request.*;
import com.naleul.naleul.domain.task.dto.response.TaskMonthlyResponse;
import com.naleul.naleul.domain.task.dto.response.TaskPageResponse;
import com.naleul.naleul.domain.task.dto.response.TaskResponse;
import com.naleul.naleul.domain.task.dto.response.TaskWeeklyResponse;
import com.naleul.naleul.domain.task.service.TaskService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Pageable;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TaskCreateRequest request
    ) {
        TaskResponse response = taskService.createTask(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.TASK_CREATED, response));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskResponse>> getTask(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId
    ) {
        TaskResponse response = taskService.getTask(userId, taskId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.TASK_FOUND, response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getTasks(
            @AuthenticationPrincipal Long userId
    ) {
        List<TaskResponse> responses = taskService.getTasksByUser(userId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.TASKS_FOUND, responses));
    }

    @GetMapping("/time-range")
    public ResponseEntity<ApiResponse<TaskPageResponse>> getTasksByTimeRange(
            @AuthenticationPrincipal Long userId,
            @ModelAttribute TaskTimeRangeRequest request,
            @PageableDefault(size = 10, sort = "plannedStartAt") Pageable pageable
    ) {
        TaskPageResponse response = taskService.getTasksByTimeRange(userId, request, pageable);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.TASKS_FOUND, response));
    }

    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getDailyTasks(
            @AuthenticationPrincipal Long userId,
            @ModelAttribute TaskDailyRequest request
    ) {
        List<TaskResponse> response = taskService.getDailyTasks(userId, request);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.TASKS_FOUND, response));
    }

    @GetMapping("/weekly")
    public ResponseEntity<ApiResponse<TaskWeeklyResponse>> getWeeklyTasks(
            @AuthenticationPrincipal Long userId,
            @ModelAttribute TaskWeeklyRequest request
    ) {
        TaskWeeklyResponse response = taskService.getWeeklyTasks(userId, request);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.TASKS_FOUND, response));
    }

    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<TaskMonthlyResponse>> getMonthlyTasks(
            @AuthenticationPrincipal Long userId,
            @ModelAttribute TaskMonthlyRequest request
    ) {
        TaskMonthlyResponse response = taskService.getMonthlyTasks(userId, request);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.TASKS_FOUND, response));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTask(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskUpdateRequest request
    ) {
        TaskResponse response = taskService.updateTask(userId, taskId, request);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.TASK_UPDATED, response));
    }

    @PatchMapping("/{taskId}/mission-schedule")
    public ResponseEntity<ApiResponse<TaskResponse>> updateMissionTaskSchedule(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId,
            @RequestBody MissionTaskScheduleRequest request
    ) {
        TaskResponse response = taskService.updateMissionTaskSchedule(userId, taskId, request);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.TASK_UPDATED, response)); // SuccessCode 이름은 실제 것으로 교체 필요
    }

    @PatchMapping("/{taskId}/complete")
    public ResponseEntity<ApiResponse<TaskResponse>> completeTask(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId
    ) {
        TaskResponse response = taskService.completeTask(userId, taskId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.TASK_UPDATED, response));
    }

    @PatchMapping("/{taskId}/cancel-complete")
    public ResponseEntity<ApiResponse<TaskResponse>> cancelCompleteTask(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId
    ) {
        TaskResponse response = taskService.cancelCompleteTask(userId, taskId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.TASK_UPDATED, response));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> deleteTask(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId
    ) {
        taskService.deleteTask(userId, taskId);
        return ResponseEntity.noContent().build();
    }
}