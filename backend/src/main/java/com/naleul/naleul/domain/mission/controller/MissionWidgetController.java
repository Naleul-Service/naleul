// controller/MissionWidgetController.java
package com.naleul.naleul.domain.mission.controller;

import com.naleul.naleul.domain.mission.dto.response.MissionWidgetResponse;
import com.naleul.naleul.domain.mission.service.MissionWidgetService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
public class MissionWidgetController {

    private final MissionWidgetService missionWidgetService;

    @GetMapping("/{missionId}/widget")
    public ResponseEntity<ApiResponse<MissionWidgetResponse>> getWidgetStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long missionId
    ) {
        MissionWidgetResponse response = missionWidgetService.getWidgetStatus(missionId, userId);
        // ⚠️ SuccessCode.MISSION_FOUND를 재사용했어요 — 전용 코드가 있으면 그걸로 바꿔주세요.
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND, response));
    }
}