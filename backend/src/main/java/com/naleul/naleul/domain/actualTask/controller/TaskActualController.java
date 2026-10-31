package com.naleul.naleul.domain.actualTask.controller;

import com.naleul.naleul.domain.actualTask.dto.request.*;
import com.naleul.naleul.domain.actualTask.dto.response.TaskActualResponse;
import com.naleul.naleul.domain.actualTask.dto.response.TaskActualWeeklyResponse;
import com.naleul.naleul.domain.actualTask.service.TaskActualService;
import com.naleul.naleul.domain.task.dto.response.TaskResponse;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/task-actuals")
@RequiredArgsConstructor
public class TaskActualController {

    private final TaskActualService taskActualService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaskActualResponse>> createActual(
            @AuthenticationPrincipal Long userId,
            @RequestBody TaskActualCreateRequest request
    ) {
        TaskActualResponse response = taskActualService.createActual(userId, request);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.ACTUAL_TASK_CREATED, response));
    }

    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<List<TaskActualResponse>>> getDailyActuals(
            @AuthenticationPrincipal Long userId,
            @ModelAttribute TaskActualDailyRequest request
    ) {
        List<TaskActualResponse> response = taskActualService.getDailyActuals(userId, request);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.ACTUAL_TASKS_FOUND, response));
    }


    @PatchMapping("/{taskId}/actual")
    public ResponseEntity<ApiResponse<TaskResponse>> recordActual(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskUpdateActualRequest request
    ) {
        TaskResponse response = taskActualService.recordActual(userId, taskId, request);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.TASK_COMPLETED, response));
    }

    @PatchMapping("/{taskActualId}")
    public ResponseEntity<ApiResponse<TaskActualResponse>> updateActual(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskActualId,
            @Valid @RequestBody TaskActualUpdateRequest request
    ) {
        TaskActualResponse response = taskActualService.updateActual(userId, taskActualId, request);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.ACTUAL_TASK_UPDATED, response));
    }

    @DeleteMapping("/{taskActualId}")
    public ResponseEntity<ApiResponse<Void>> deleteActual(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskActualId
    ) {
        taskActualService.deleteActual(userId, taskActualId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.ACTUAL_TASK_DELETED, null));
    }

    // TaskActualController.java — 추가

    @GetMapping("/weekly")
    public ResponseEntity<ApiResponse<TaskActualWeeklyResponse>> getWeeklyActuals(
            @AuthenticationPrincipal Long userId,
            @ModelAttribute TaskActualWeeklyRequest request
    ) {
        TaskActualWeeklyResponse response = taskActualService.getWeeklyActuals(userId, request);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.ACTUAL_TASKS_FOUND, response));
    }
}