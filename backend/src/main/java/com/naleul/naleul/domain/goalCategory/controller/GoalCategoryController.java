package com.naleul.naleul.domain.goalCategory.controller;

import com.naleul.naleul.domain.habit.service.HabitGoalProvisioner;

import com.naleul.naleul.domain.goalCategory.dto.request.*;
import com.naleul.naleul.domain.goalCategory.dto.response.GoalCategoryResponse;
import com.naleul.naleul.domain.goalCategory.reader.GoalCategoryReader;
import com.naleul.naleul.domain.goalCategory.service.GoalCategoryService;
import com.naleul.naleul.domain.goalCategory.service.RecordGoalService;
import com.naleul.naleul.domain.planner.dto.request.GoalTaskRequest;
import com.naleul.naleul.domain.planner.dto.response.GoalTasksResponse;
import com.naleul.naleul.domain.planner.dto.response.TimeBlockTaskResponse;
import com.naleul.naleul.domain.planner.service.GoalTaskCommandService;
import com.naleul.naleul.domain.planner.service.GoalTaskQueryService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/goal-categories")
public class GoalCategoryController {
    private final GoalCategoryService goalCategoryService;
    private final RecordGoalService recordGoalService;
    private final GoalCategoryReader goalCategoryReader;
    private final GoalTaskQueryService goalTaskQueryService;
    private final GoalTaskCommandService goalTaskCommandService;
    private final HabitGoalProvisioner habitGoalProvisioner;

    // 목표 카테고리 생성
    @PostMapping
    public ResponseEntity<ApiResponse<GoalCategoryResponse>> create(
            @AuthenticationPrincipal Long userId,
            @RequestBody GoalCategoryCreateRequest request
    ) {
        GoalCategoryResponse response = goalCategoryService.create(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.GOAL_CREATED, response));
    }

    /**
     * 기록형 목표 만들기 (V39) — 이름만으로 바로 생성 (AI 설계·수치·마일스톤 없음)
     * POST /goal-categories/record  { "name": "나를 업무", "emoji": "💼" }
     */
    @PostMapping("/record")
    public ResponseEntity<ApiResponse<GoalCategoryResponse>> createRecord(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RecordGoalCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.GOAL_CREATED, recordGoalService.create(userId, request)));
    }

    /**
     * 임시 목표를 기록형 목표로 그대로 두기 (구체화 없이)
     * PATCH /goal-categories/{id}/keep-as-record
     */
    @PatchMapping("/{goalCategoryId}/keep-as-record")
    public ResponseEntity<ApiResponse<GoalCategoryResponse>> keepAsRecord(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_UPDATED,
                recordGoalService.keepTemporaryAsRecord(userId, goalCategoryId)));
    }

    /**
     * 예전에 직접 만든 목표를 업무형으로 바꾸기 (2026-10, 목표 상세의 "어떻게 이어갈까요?" 카드)
     * PATCH /goal-categories/{id}/convert-to-record
     */
    @PatchMapping("/{goalCategoryId}/convert-to-record")
    public ResponseEntity<ApiResponse<GoalCategoryResponse>> convertToRecord(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_UPDATED,
                recordGoalService.convertToRecord(userId, goalCategoryId)));
    }

    // 목표 카테고리 단건 조회
    @GetMapping("/{goalCategoryId}")
    public ResponseEntity<ApiResponse<GoalCategoryResponse>> getGoalCategory(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId
    ) {
        GoalCategoryResponse response = goalCategoryReader.getGoalCategory(goalCategoryId, userId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.GOAL_FOUND, response));
    }

    // 목표에 속한 Task 전체 (AI 생성·직접 추가·루틴, 미완료 먼저 · 오늘과 가까운 순)
    @GetMapping("/{goalCategoryId}/tasks")
    public ResponseEntity<ApiResponse<GoalTasksResponse>> getGoalTasks(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.GOAL_TASKS_FOUND,
                        goalTaskQueryService.getGoalTasks(userId, goalCategoryId)));
    }

    // 목표 상세에서 Task 직접 추가 (날짜 필수, 시간은 선택 — 없으면 자동 배치)
    @PostMapping("/{goalCategoryId}/tasks")
    public ResponseEntity<ApiResponse<TimeBlockTaskResponse>> createGoalTask(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GoalTaskRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.GOAL_TASK_CREATED,
                        goalTaskCommandService.create(userId, goalCategoryId, request)));
    }

    // 목표 상세에서 Task 수정 (일회성 Task 만, 루틴·미션 제외)
    @PutMapping("/{goalCategoryId}/tasks/{taskId}")
    public ResponseEntity<ApiResponse<TimeBlockTaskResponse>> updateGoalTask(
            @PathVariable Long goalCategoryId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GoalTaskRequest request
    ) {
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.GOAL_TASK_UPDATED,
                        goalTaskCommandService.update(userId, goalCategoryId, taskId, request)));
    }

    /**
     * 목표 카테고리 전체 조회 (내 목표만)
     * includeHabit=true 면 맨 앞에 기본 생활 습관 그릇(goalMode = HABIT, V52)도 넣는다 (없으면 만든다).
     * 기본값 false — 습관 그릇을 모르는 예전 앱 목록에 빈 목표가 보이지 않게.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<GoalCategoryResponse>>> getGoalCategories(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "false") boolean includeHabit
    ) {
        List<GoalCategoryResponse> response = goalCategoryReader.getGoalCategories(userId);
        if (includeHabit) {
            List<GoalCategoryResponse> withHabit = new java.util.ArrayList<>();
            withHabit.add(habitGoalProvisioner.ensureResponse(userId));
            withHabit.addAll(response);
            response = withHabit;
        }
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.GOAL_FOUND, response));
    }

    // 목표 카테고리 수정
    @PatchMapping("/{goalCategoryId}")
    public ResponseEntity<ApiResponse<GoalCategoryResponse>> update(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId,
            @RequestBody GoalCategoryUpdateRequest request

    ) {
        GoalCategoryResponse response = goalCategoryService.update(goalCategoryId, userId, request);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.GOAL_UPDATED, response));
    }

    // 목표 완료 처리
    @PatchMapping("/{goalCategoryId}/complete")
    public ResponseEntity<ApiResponse<GoalCategoryResponse>> complete(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GoalCategoryCompleteRequest request

    ) {
        GoalCategoryResponse response = goalCategoryService.complete(goalCategoryId, userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.GOAL_COMPLETED, response));
    }

    @PatchMapping("/{goalCategoryId}/achievement")
    public ResponseEntity<ApiResponse<GoalCategoryResponse>> fillAchievement(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GoalCategoryAchievementRequest request
    ) {
        GoalCategoryResponse response = goalCategoryService.fillAchievement(goalCategoryId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_ACHIEVEMENT_FILLED, response));
    }

    // 목표 카테고리 소프트 삭제
    @DeleteMapping("/{goalCategoryId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId
    ) {
        goalCategoryService.delete(goalCategoryId, userId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.GOAL_DELETED, null));
    }
}
