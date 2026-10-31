package com.naleul.naleul.domain.mission.controller;

import com.naleul.naleul.domain.mission.dto.request.MissionTaskCompleteRequest;
import com.naleul.naleul.domain.mission.dto.response.MissionTaskCompleteResponse;
import com.naleul.naleul.domain.mission.dto.response.TodayMissionTaskResponse;
import com.naleul.naleul.domain.mission.service.MissionTaskCompleteService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/missions/tasks")
@RequiredArgsConstructor
public class MissionTaskCompleteController {

    private final MissionTaskCompleteService missionTaskCompleteService;

    // Task 완료 처리 + 인증 기록 저장
    @PostMapping("/{taskId}/complete")
    public ResponseEntity<ApiResponse<MissionTaskCompleteResponse>> completeTask(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId,
            @Valid @RequestBody MissionTaskCompleteRequest request
    ) {
        MissionTaskCompleteResponse response =
                missionTaskCompleteService.completeTask(userId, taskId, request);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_TASK_COMPLETED, response));
    }

    // 인증 기록 조회 (팀원들도 볼 수 있음)
    @GetMapping("/{taskId}/complete")
    public ResponseEntity<ApiResponse<MissionTaskCompleteResponse>> getCompleteRecord(
            @PathVariable Long taskId
    ) {
        MissionTaskCompleteResponse response =
                missionTaskCompleteService.getCompleteRecord(taskId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND, response));
    }

    // GET /api/v1/missions/{missionId}/today-task
    @GetMapping("/{missionId}/today-task")  // ← MissionController에 추가하는 게 더 자연스럽지만
    public ResponseEntity<ApiResponse<TodayMissionTaskResponse>> getTodayTask(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long missionId
    ) {
        TodayMissionTaskResponse response =
                missionTaskCompleteService.getTodayMissionTask(userId, missionId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND, response));
    }
}