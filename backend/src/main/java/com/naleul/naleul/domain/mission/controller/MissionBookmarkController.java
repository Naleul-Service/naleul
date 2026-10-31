package com.naleul.naleul.domain.mission.controller;

import com.naleul.naleul.domain.mission.dto.response.MissionBookmarkResponse;
import com.naleul.naleul.domain.mission.service.MissionBookmarkService;
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
public class MissionBookmarkController {

    private final MissionBookmarkService missionBookmarkService;

    // 찜 토글
    @PostMapping("/{missionId}/bookmark")
    public ResponseEntity<ApiResponse<Boolean>> toggleBookmark(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long missionId
    ) {
        boolean isBookmarked = missionBookmarkService.toggleBookmark(userId, missionId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_UPDATED, isBookmarked));
    }

    // 내가 찜한 미션 목록
    @GetMapping("/bookmarks/me")
    public ResponseEntity<ApiResponse<List<MissionBookmarkResponse>>> getMyBookmarks(
            @AuthenticationPrincipal Long userId
    ) {
        List<MissionBookmarkResponse> response = missionBookmarkService.getMyBookmarks(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND, response));
    }
}