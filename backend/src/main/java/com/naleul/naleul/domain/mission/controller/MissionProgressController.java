package com.naleul.naleul.domain.mission.controller;

import com.naleul.naleul.domain.mission.dto.response.MissionDailyDetailResponse;
import com.naleul.naleul.domain.mission.dto.response.MissionGrassMapResponse;
import com.naleul.naleul.domain.mission.dto.response.MissionMonthlyPhotoResponse;
import com.naleul.naleul.domain.mission.dto.response.PartyAchievementResponse;
import com.naleul.naleul.domain.mission.service.MissionPokeService;
import com.naleul.naleul.domain.mission.service.MissionProgressService;
import com.naleul.naleul.domain.mission.service.PartyAchievementService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
public class MissionProgressController {

    private final MissionProgressService missionProgressService;
    private final PartyAchievementService partyAchievementService;
    private final MissionPokeService missionPokeService;

    // ✅ @AuthenticationPrincipal Long userId → @RequestParam Long userId로 교체
    @GetMapping("/{missionId}/grass")
    public ResponseEntity<ApiResponse<MissionGrassMapResponse>> getGrassMap(
            @AuthenticationPrincipal Long loginUserId,  // 인증용 (지금은 안 쓰지만 보안상 유지)
            @PathVariable Long missionId,
            @RequestParam Long userId                   // ✅ 조회할 대상 유저 id
    ) {
        MissionGrassMapResponse response = missionProgressService.getGrassMap(userId, missionId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND, response));
    }



    // 월별 인증사진 조회
    @GetMapping("/{missionId}/photos")
    public ResponseEntity<ApiResponse<MissionMonthlyPhotoResponse>> getMonthlyPhotos(
            @PathVariable Long missionId,
            @RequestParam Long userId,
            @RequestParam int year,
            @RequestParam int month
    ) {
        MissionMonthlyPhotoResponse response =
                missionProgressService.getMonthlyPhotos(userId, missionId, year, month);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND, response));
    }

    // 날짜별 상세 (달성률 + 참여자별 인증 여부/사진). 캘린더/히트맵에서 날짜 탭하면 호출.
    // 예: GET /api/v1/missions/1/days/2026-06-01
    @GetMapping("/{missionId}/days/{date}")
    public ResponseEntity<ApiResponse<MissionDailyDetailResponse>> getDailyDetail(
            @PathVariable Long missionId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        MissionDailyDetailResponse response = missionProgressService.getDailyDetail(missionId, date);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND, response));
    }

    // 파티 달성률, 진행률 인원 등
    @GetMapping("/{missionId}/party-achievement")
    public ResponseEntity<ApiResponse<PartyAchievementResponse>> getPartyAchievement(
            @PathVariable Long missionId
    ) {
        PartyAchievementResponse response =
                partyAchievementService.getPartyAchievement(missionId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND, response));
    }

    // 쪼기 — 아직 인증 안 한 참여자에게 인증 재촉 알림 발송
    @PostMapping("/{missionId}/participants/{targetUserId}/poke")
    public ResponseEntity<ApiResponse<Void>> pokeParticipant(
            @AuthenticationPrincipal Long loginUserId,
            @PathVariable Long missionId,
            @PathVariable Long targetUserId
    ) {
        missionPokeService.poke(loginUserId, missionId, targetUserId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND));
    }
}