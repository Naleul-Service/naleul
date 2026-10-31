package com.naleul.naleul.domain.routine.controller;

import com.naleul.naleul.domain.routine.dto.request.RoutineCompleteRequest;
import com.naleul.naleul.domain.routine.dto.request.RoutineCreateRequest;
import com.naleul.naleul.domain.routine.dto.request.RoutineExtraDayRequest;
import com.naleul.naleul.domain.routine.dto.request.RoutineUpdateRequest;
import com.naleul.naleul.domain.routine.dto.response.RoutineHeatmapResponse;
import com.naleul.naleul.domain.routine.dto.response.RoutineResponse;
import com.naleul.naleul.domain.routine.reader.RoutineReader;
import com.naleul.naleul.domain.routine.service.RoutineExtraDayService;
import com.naleul.naleul.domain.routine.service.RoutineService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/routines")
public class RoutineController {
    private final RoutineService routineService;
    private final RoutineExtraDayService routineExtraDayService;
    private final RoutineReader routineReader;
    // 루틴 생성
    @PostMapping
    public ResponseEntity<ApiResponse<RoutineResponse>> create(
            @RequestBody RoutineCreateRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        RoutineResponse response = routineService.create(request, userId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.ROUTINE_CREATED, response));
    }

    // 루틴 전체 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoutineResponse>>> getAll(@AuthenticationPrincipal Long userId) {
        List<RoutineResponse> response = routineReader.getRoutines(userId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.ROUTINES_FOUND, response));
    }

    // 전체 루틴 보기 (카테고리 색상 포함, N+1 방지 조회)
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<RoutineResponse>>> getAllForDisplay(
            @AuthenticationPrincipal Long userId
    ) {
        List<RoutineResponse> response = routineReader.getAllRoutinesForDisplay(userId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.ROUTINES_FOUND, response));
    }

    //히트맵
    @GetMapping("/{goalCategoryId}/heatmap")
    public ResponseEntity<ApiResponse<List<RoutineHeatmapResponse>>> getHeatmap(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long goalCategoryId
    ) {
        List<RoutineHeatmapResponse> response = routineReader.getHeatmap(userId, goalCategoryId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.ROUTINE_FOUND, response));
    }


    // 루틴 단건 조회
    @GetMapping("/{routineId}")
    public ResponseEntity<ApiResponse<RoutineResponse>> getRoutine(
            @PathVariable Long routineId,
            @AuthenticationPrincipal Long userId
    ) {
        RoutineResponse response = routineReader.getRoutine(userId, routineId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.ROUTINE_FOUND, response));
    }

    // 루틴 수정
    @PatchMapping("/{routineId}")
    public ResponseEntity<ApiResponse<RoutineResponse>> update(
            @PathVariable Long routineId,
            @AuthenticationPrincipal Long userId,
            @RequestBody RoutineUpdateRequest request
    ) {
        RoutineResponse response = routineService.update(userId, routineId, request);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.ROUTINE_UPDATED, response));
    }

    // 루틴 상태 변경(완료 처리)
    @PatchMapping("/{routineId}/complete")
    public ResponseEntity<ApiResponse<RoutineResponse>> complete(
            @PathVariable Long routineId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RoutineCompleteRequest request
    ) {
        RoutineResponse response = routineService.complete(routineId, userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.ROUTINE_COMPLETED, response));
    }

    // 루틴 삭제
    /**
     * 반복 요일이 아닌 날에 한 번 더 (평일 루틴을 주말에도 했어요)
     * POST /routines/{id}/extra-days { "date", "startTime"?, "endTime"?, "done" } → { "taskId" }
     */
    @PostMapping("/{routineId}/extra-days")
    public ResponseEntity<ApiResponse<Map<String, Long>>> addExtraDay(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long routineId,
            @Valid @RequestBody RoutineExtraDayRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.ROUTINE_UPDATED, routineExtraDayService.add(userId, routineId, request)));
    }

        @DeleteMapping("/{routineId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long routineId,
            @AuthenticationPrincipal Long userId
    ) {
        routineService.delete(routineId, userId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.ROUTINE_DELETED, null));
    }

}
