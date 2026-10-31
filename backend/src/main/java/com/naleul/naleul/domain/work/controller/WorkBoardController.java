package com.naleul.naleul.domain.work.controller;

import com.naleul.naleul.domain.work.dto.request.*;
import com.naleul.naleul.domain.work.dto.response.WorkBoardResponse;
import com.naleul.naleul.domain.work.dto.response.WorkShareTextResponse;
import com.naleul.naleul.domain.work.dto.response.WorkTaskItem;
import com.naleul.naleul.domain.work.dto.response.WorkTopicResponse;
import com.naleul.naleul.domain.work.service.WorkBoardService;
import com.naleul.naleul.domain.work.service.WorkTaskService;
import com.naleul.naleul.domain.work.service.WorkTopicService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 업무형 목표 상세 · 보드 (V52) — 모두 /api/v1/goal-categories/{goalCategoryId} 아래, 업무형(RECORD) 목표만
 *
 * GET    /work-board?archiveDays=30               상세 한 화면 (요약 · 오늘 · 마감 임박 · 주제 · 보드 · 할 일 목록 · 아카이브 · 회고)
 * GET    /work-board/share-text?scope=REMAINING   공유 텍스트 (TODAY · REMAINING · ALL)
 * POST   /work-topics                             주제 만들기 (V53: dueDate 주제 마감)
 * PATCH  /work-topics/{topicId}                   주제 고치기 (이름 · 마감 · clearDueDate · completed 끝냈어요/다시 열기)
 * DELETE /work-topics/{topicId}                   주제 지우기 (Task 는 "주제 없음"으로)
 * PUT    /work-topics/order                       주제 순서
 * POST   /work-tasks                              Task 하나 추가 (날짜 없으면 "할 일 목록")
 * POST   /work-tasks/bulk                         여러 줄 한 번에 추가
 * PATCH  /work-tasks/{taskId}                     Task 고치기 (이름 · 주제 · 날짜 · 마감 · 메모 · 칸)
 * PATCH  /work-tasks/{taskId}/status              보드 칸 옮기기
 * V53: 업무형은 목표 전체 마감이 없다 — 마감은 주제마다 (응답의 timeline 이 주제를 마감 순서대로 잇는 로드맵)
 * Task 삭제는 기존 DELETE /api/v1/tasks/{taskId}, 회고는 기존 /api/v1/retrospectives (goalCategoryId 지정)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/goal-categories/{goalCategoryId}")
public class WorkBoardController {

    private final WorkBoardService workBoardService;
    private final WorkTaskService workTaskService;
    private final WorkTopicService workTopicService;

    @GetMapping("/work-board")
    public ResponseEntity<ApiResponse<WorkBoardResponse>> board(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Integer archiveDays
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.WORK_BOARD_FOUND,
                workBoardService.board(userId, goalCategoryId, archiveDays)));
    }

    @GetMapping("/work-board/share-text")
    public ResponseEntity<ApiResponse<WorkShareTextResponse>> shareText(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "REMAINING") String scope
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.WORK_BOARD_FOUND,
                new WorkShareTextResponse(scope, workBoardService.shareText(userId, goalCategoryId, scope))));
    }

    // ── 주제 ─────────────────────────────────────────────

    @PostMapping("/work-topics")
    public ResponseEntity<ApiResponse<WorkTopicResponse>> createTopic(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody WorkTopicRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(SuccessCode.WORK_TOPIC_SAVED,
                WorkTopicResponse.from(workTopicService.create(userId, goalCategoryId, request))));
    }

    @PatchMapping("/work-topics/{topicId}")
    public ResponseEntity<ApiResponse<WorkTopicResponse>> updateTopic(
            @PathVariable Long goalCategoryId,
            @PathVariable Long topicId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody WorkTopicRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.WORK_TOPIC_SAVED,
                WorkTopicResponse.from(workTopicService.update(userId, goalCategoryId, topicId, request))));
    }

    @DeleteMapping("/work-topics/{topicId}")
    public ResponseEntity<ApiResponse<Void>> deleteTopic(
            @PathVariable Long goalCategoryId,
            @PathVariable Long topicId,
            @AuthenticationPrincipal Long userId
    ) {
        workTopicService.delete(userId, goalCategoryId, topicId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.WORK_TOPIC_DELETED, null));
    }

    @PutMapping("/work-topics/order")
    public ResponseEntity<ApiResponse<Void>> reorderTopics(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody WorkTopicOrderRequest request
    ) {
        workTopicService.reorder(userId, goalCategoryId, request);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.WORK_TOPIC_SAVED, null));
    }

    // ── Task ─────────────────────────────────────────────

    @PostMapping("/work-tasks")
    public ResponseEntity<ApiResponse<WorkTaskItem>> createTask(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody WorkTaskCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(SuccessCode.WORK_TASKS_CREATED,
                workTaskService.create(userId, goalCategoryId, request)));
    }

    @PostMapping("/work-tasks/bulk")
    public ResponseEntity<ApiResponse<List<WorkTaskItem>>> createTasks(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody WorkTaskBulkRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(SuccessCode.WORK_TASKS_CREATED,
                workTaskService.createBulk(userId, goalCategoryId, request)));
    }

    @PatchMapping("/work-tasks/{taskId}")
    public ResponseEntity<ApiResponse<WorkTaskItem>> updateTask(
            @PathVariable Long goalCategoryId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody WorkTaskUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.WORK_TASK_UPDATED,
                workTaskService.update(userId, goalCategoryId, taskId, request)));
    }

    @PatchMapping("/work-tasks/{taskId}/status")
    public ResponseEntity<ApiResponse<WorkTaskItem>> moveTask(
            @PathVariable Long goalCategoryId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody WorkTaskStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.WORK_TASK_UPDATED,
                workTaskService.changeStatus(userId, goalCategoryId, taskId, request.status())));
    }
}
