package com.naleul.naleul.domain.mission.controller;

import com.naleul.naleul.domain.mission.dto.response.MissionActiveParticipantResponse;
import com.naleul.naleul.domain.mission.dto.response.PendingParticipantResponse;
import com.naleul.naleul.domain.mission.service.MissionAdminService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/missions/{missionId}/admin")
@RequiredArgsConstructor
public class MissionAdminController {

    private final MissionAdminService missionAdminService;

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<PendingParticipantResponse>>> getPendingParticipants(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long missionId
    ) {
        List<PendingParticipantResponse> response =
                missionAdminService.getPendingParticipants(userId, missionId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND, response));
    }

    @GetMapping("/participants")
    public ResponseEntity<ApiResponse<List<MissionActiveParticipantResponse>>> getActiveParticipants(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long missionId
    ) {
        List<MissionActiveParticipantResponse> response =
                missionAdminService.getActiveParticipants(userId, missionId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND, response));
    }

    @PatchMapping("/participants/{participantId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveParticipant(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long missionId,
            @PathVariable Long participantId
    ) {
        missionAdminService.approveParticipant(userId, missionId, participantId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_UPDATED, null));
    }

    @PatchMapping("/participants/{participantId}/cancel-approval")
    public ResponseEntity<ApiResponse<Void>> cancelApproval(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long missionId,
            @PathVariable Long participantId
    ) {
        missionAdminService.cancelApproval(userId, missionId, participantId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_UPDATED, null));
    }

    @PatchMapping("/participants/{participantId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectParticipant(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long missionId,
            @PathVariable Long participantId
    ) {
        missionAdminService.rejectParticipant(userId, missionId, participantId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_UPDATED, null));
    }

    // ❌ updateParticipantStatus 엔드포인트 삭제 — 방장 수동 판정 제거
}