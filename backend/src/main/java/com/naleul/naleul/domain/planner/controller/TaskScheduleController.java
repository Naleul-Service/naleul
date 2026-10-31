package com.naleul.naleul.domain.planner.controller;

import com.naleul.naleul.domain.planner.dto.request.RoutineScheduleRequest;
import com.naleul.naleul.domain.planner.dto.request.TaskBatchScheduleRequest;
import com.naleul.naleul.domain.planner.dto.request.TaskScheduleRequest;
import com.naleul.naleul.domain.planner.dto.request.TaskMoveDateRequest;
import com.naleul.naleul.domain.planner.dto.request.TaskPriorityRequest;
import com.naleul.naleul.domain.planner.dto.response.RoutineScheduleResponse;
import com.naleul.naleul.domain.planner.dto.response.TaskBatchScheduleResponse;
import com.naleul.naleul.domain.planner.dto.response.TaskQuotaResponse;
import com.naleul.naleul.domain.planner.dto.response.TaskScheduleResponse;
import com.naleul.naleul.domain.planner.dto.response.TimeBlockTaskResponse;
import com.naleul.naleul.domain.planner.service.RoutineScheduleService;
import com.naleul.naleul.domain.planner.service.TaskScheduleService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 캘린더 블록 조작 (기존 TaskController 를 건드리지 않으려고 분리, 같은 /api/v1/tasks 경로 사용)
 *
 * PATCH  /api/v1/tasks/{taskId}/schedule   드래그로 시간 변경 (잠금)
 * PATCH  /api/v1/tasks/{taskId}/routine-schedule  루틴 시간 변경 + 범위 (이날만 / 이번 주 / 앞으로)
 * DELETE /api/v1/tasks/{taskId}/schedule   시간 미정으로 되돌리기
 * PATCH  /api/v1/tasks/{taskId}/unlock     잠금 해제
 * PATCH  /api/v1/tasks/{taskId}/priority   우선순위만 바꾸기 (루틴 · 미션은 그날만)
 * PATCH  /api/v1/tasks/{taskId}/carry-over/revert  이월 되돌리기 (원래 날짜 · 시각으로)
 * GET    /api/v1/tasks/quota               Free 하루 추가 한도
 *
 * ※ GET /quota 와 기존 GET /{taskId} 는 경로가 겹쳐 보이지만, Spring 은 글자 그대로인 경로(/quota)를
 *    변수 경로(/{taskId})보다 먼저 고르므로 충돌하지 않는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tasks")
public class TaskScheduleController {

    private final TaskScheduleService taskScheduleService;
    private final RoutineScheduleService routineScheduleService;

    @PatchMapping("/{taskId}/schedule")
    public ResponseEntity<ApiResponse<TaskScheduleResponse>> reschedule(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskScheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.TASK_SCHEDULED,
                taskScheduleService.reschedule(userId, taskId, request)));
    }

    /** 여러 Task 를 한 번에 옮기기 (묶음 드래그). 하나라도 자리가 없으면 409, 아무것도 바뀌지 않음 */
    @PatchMapping("/schedule/batch")
    public ResponseEntity<ApiResponse<TaskBatchScheduleResponse>> rescheduleBatch(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TaskBatchScheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.TASK_SCHEDULED,
                taskScheduleService.rescheduleBatch(userId, request.items())));
    }

    /** 루틴 블록 시간 변경 + 적용 범위 (이날만 / 이번 주 / 앞으로) */
    @PatchMapping("/{taskId}/routine-schedule")
    public ResponseEntity<ApiResponse<RoutineScheduleResponse>> rescheduleRoutine(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId,
            @Valid @RequestBody RoutineScheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.ROUTINE_SCHEDULE_CHANGED,
                routineScheduleService.change(userId, taskId, request)));
    }

    /** 다른 날로 옮기기 — 내일 / 모레 / 날짜 지정 · 시간 미정끼리 드래그 */
    @PatchMapping("/{taskId}/date")
    public ResponseEntity<ApiResponse<TimeBlockTaskResponse>> moveToDate(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskMoveDateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.TASK_DATE_MOVED,
                taskScheduleService.moveToDate(userId, taskId, request)));
    }

    @DeleteMapping("/{taskId}/schedule")
    public ResponseEntity<ApiResponse<TimeBlockTaskResponse>> unschedule(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.TASK_UNSCHEDULED,
                taskScheduleService.unschedule(userId, taskId)));
    }

    @PatchMapping("/{taskId}/unlock")
    public ResponseEntity<ApiResponse<TimeBlockTaskResponse>> unlock(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.TASK_UNLOCKED,
                taskScheduleService.unlock(userId, taskId)));
    }

    /** 우선순위만 바꾸기 — 시간 · 날짜는 그대로, 루틴 · 미션은 그날 Task 만 */
    @PatchMapping("/{taskId}/priority")
    public ResponseEntity<ApiResponse<TimeBlockTaskResponse>> changePriority(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskPriorityRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.TASK_UPDATED,
                taskScheduleService.changePriority(userId, taskId, request.taskPriority())));
    }

    /** 22시 이월 제안: 옮기기 (점선 → 확정) */
    @PatchMapping("/{taskId}/carry-over/accept")
    public ResponseEntity<ApiResponse<TimeBlockTaskResponse>> acceptCarry(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.TASK_SCHEDULED,
                taskScheduleService.acceptCarry(userId, taskId)));
    }

    /** 22시 이월 제안: 안 옮기기 (시간 미정으로) */
    @PatchMapping("/{taskId}/carry-over/decline")
    public ResponseEntity<ApiResponse<TimeBlockTaskResponse>> declineCarry(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.TASK_SCHEDULED,
                taskScheduleService.declineCarry(userId, taskId)));
    }

    /**
     * 이월 되돌리기 — 원래 날짜 · 시각으로 (자리가 없으면 그날 시간 미정). 자동 이월이 다시 옮기지 않음.
     * ?from=yyyy-MM-dd : 누른 흔적이 있는 날로 (없으면 마지막 이월의 원래 날). 이미 그날에 있으면 그대로 돌려줌
     */
    @PatchMapping("/{taskId}/carry-over/revert")
    public ResponseEntity<ApiResponse<TimeBlockTaskResponse>> revertCarry(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.TASK_SCHEDULED,
                taskScheduleService.revertCarry(userId, taskId, from)));
    }

    /** 그날 이월 제안 모두 옮기기 — 응답: 확정한 개수 */
    @PatchMapping("/carry-over/accept-all")
    public ResponseEntity<ApiResponse<Integer>> acceptAllCarry(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.TASK_SCHEDULED,
                taskScheduleService.acceptAllCarry(userId, date)));
    }

    @GetMapping("/quota")
    public ResponseEntity<ApiResponse<TaskQuotaResponse>> quota(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.TASK_QUOTA_FOUND,
                taskScheduleService.quota(userId)));
    }
}
