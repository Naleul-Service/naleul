package com.naleul.naleul.domain.mission.controller;

import com.naleul.naleul.domain.mission.dto.response.MissionBadgeResponse;
import com.naleul.naleul.domain.mission.dto.response.PersonalAchievementResponse;
import com.naleul.naleul.domain.mission.dto.response.TeamAchievementResponse;
import com.naleul.naleul.domain.mission.enums.MissionCategoryItem;
import com.naleul.naleul.domain.mission.service.MissionAchievementService;
import com.naleul.naleul.domain.mission.service.MissionBadgeService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
public class MissionAchievementController {

    private final MissionAchievementService missionAchievementService;
    private final MissionBadgeService missionBadgeService;

    // 개인 달성 목록
    @GetMapping("/achievements/personal")
    public ResponseEntity<ApiResponse<List<PersonalAchievementResponse>>> getPersonalAchievements(
            @AuthenticationPrincipal Long userId
    ) {
        List<PersonalAchievementResponse> response =
                missionAchievementService.getPersonalAchievements(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND, response));
    }

    // 팀 달성 목록
    @GetMapping("/achievements/team")
    public ResponseEntity<ApiResponse<List<TeamAchievementResponse>>> getTeamAchievements(
            @AuthenticationPrincipal Long userId
    ) {
        List<TeamAchievementResponse> response =
                missionAchievementService.getTeamAchievements(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND, response));
    }

    // 획득한 뱃지 목록 (퍼즐 제외 — 업적성 뱃지만)
    @GetMapping("/achievements/badges")
    public ResponseEntity<ApiResponse<List<MissionBadgeResponse>>> getMyBadges(
            @AuthenticationPrincipal Long userId
    ) {
        List<MissionBadgeResponse> response = missionBadgeService.getMyBadges(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND, response));
    }

    // ✅ 신규 — 카테고리 항목별 퍼즐 목록. categoryItem 쿼리파라미터 없으면 전체 항목 조회
    // 예: GET /api/v1/missions/achievements/puzzles
    //     GET /api/v1/missions/achievements/puzzles?categoryItem=EXERCISE
    @GetMapping("/achievements/puzzles")
    public ResponseEntity<ApiResponse<List<MissionBadgeResponse>>> getMyPuzzles(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) MissionCategoryItem categoryItem
    ) {
        List<MissionBadgeResponse> response = missionBadgeService.getMyPuzzles(userId, categoryItem);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND, response));
    }

    @GetMapping("/achievements/personal/unconfirmed")
    public ResponseEntity<ApiResponse<List<PersonalAchievementResponse>>> getUnconfirmedResults(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                SuccessCode.MISSION_FOUND, missionAchievementService.getUnconfirmedResults(userId)));
    }

    @GetMapping("/achievements/badges/unconfirmed")
    public ResponseEntity<ApiResponse<List<MissionBadgeResponse>>> getUnconfirmedBadges(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                SuccessCode.MISSION_FOUND, missionBadgeService.getUnconfirmedBadges(userId)));
    }

    @PatchMapping("/{missionId}/achievements/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmResult(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long missionId
    ) {
        missionAchievementService.confirmResult(userId, missionId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND, null));
    }

    @PatchMapping("/achievements/badges/{badgeId}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmBadge(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long badgeId
    ) {
        missionBadgeService.confirmBadge(userId, badgeId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND, null));
    }
}