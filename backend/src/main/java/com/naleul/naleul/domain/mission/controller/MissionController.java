package com.naleul.naleul.domain.mission.controller;

import com.naleul.naleul.domain.mission.dto.request.MissionCreateRequest;
import com.naleul.naleul.domain.mission.dto.request.MissionJoinRequest;
import com.naleul.naleul.domain.mission.dto.request.MissionPasswordCheckRequest;
import com.naleul.naleul.domain.mission.dto.request.MissionUpdateRequest;
import com.naleul.naleul.domain.mission.dto.response.*;
import com.naleul.naleul.domain.mission.service.MissionDailyStatusService;
import com.naleul.naleul.domain.mission.service.MissionService;
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
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
public class MissionController {

    private final MissionService missionService;
    private final MissionDailyStatusService missionDailyStatusService;

    @PostMapping
    public ResponseEntity<ApiResponse<MissionResponse>> createMission(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody MissionCreateRequest request
    ) {
        MissionResponse response = missionService.createMission(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.MISSION_CREATED, response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MissionListResponse>>> getMissions(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Boolean isPrivate,
            @RequestParam(required = false) String keyword
    ) {
        List<MissionListResponse> response = missionService.getMissions(userId, isPrivate, keyword);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.MISSION_FOUND, response));
    }

    // ✅ 신규 — "미션 만들기" 버튼 탭 시점에 생성 가능 여부를 미리 확인
    @GetMapping("/create-eligibility")
    public ResponseEntity<ApiResponse<Boolean>> checkCreateEligibility(
            @AuthenticationPrincipal Long userId
    ) {
        boolean eligible = missionService.canCreateMission(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.OK, eligible));
    }

    @GetMapping("/{missionId}")
    public ResponseEntity<ApiResponse<MissionDetailResponse>> getMissionDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long missionId
    ) {
        MissionDetailResponse response = missionService.getMissionDetail(userId, missionId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.MISSION_FOUND, response));
    }

    // MissionController.java에 추가
    @PostMapping("/{missionId}/join")
    public ResponseEntity<ApiResponse<Void>> joinMission(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long missionId,
            @Valid @RequestBody MissionJoinRequest request
    ) {
        missionService.joinMission(userId, missionId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.MISSION_JOINED, null));
    }

    @PostMapping("/{missionId}/check-password")
    public ResponseEntity<ApiResponse<MissionPasswordCheckResponse>> checkPassword(
            @PathVariable Long missionId,
            @Valid @RequestBody MissionPasswordCheckRequest request
    ) {
        MissionPasswordCheckResponse response = missionService.checkPassword(missionId, request);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND, response));
    }

    // MissionController.java에 추가
    @PutMapping("/{missionId}")
    public ResponseEntity<ApiResponse<MissionResponse>> updateMission(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long missionId,
            @Valid @RequestBody MissionUpdateRequest request
    ) {
        MissionResponse response = missionService.updateMission(userId, missionId, request);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_UPDATED, response));
    }

    @DeleteMapping("/{missionId}")
    public ResponseEntity<ApiResponse<Void>> deleteMission(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long missionId
    ) {
        missionService.deleteMission(userId, missionId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_DELETED, null));
    }

    @GetMapping("/{missionId}/daily-status")
    public ResponseEntity<ApiResponse<MissionDailyStatusResponse>> getDailyStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long missionId
    ) {
        MissionDailyStatusResponse response = missionDailyStatusService.getDailyStatus(userId, missionId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND, response));
    }

    // ⚠️ 새 파일이 아니라, joinMission 엔드포인트가 있는 컨트롤러(아마 MissionController.java)에
    // 추가할 코드입니다. joinMission 핸들러 바로 아래에 추가해주세요.
    // missionService는 이미 그 컨트롤러에 주입돼 있을 거예요.

    // 신청 취소
    @DeleteMapping("/{missionId}/join")
    public ResponseEntity<ApiResponse<Void>> cancelJoin(
            @AuthenticationPrincipal Long loginUserId,
            @PathVariable Long missionId
    ) {
        missionService.cancelJoin(loginUserId, missionId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND)); // ⚠️ 더 적절한 SuccessCode 있으면 교체
    }

}