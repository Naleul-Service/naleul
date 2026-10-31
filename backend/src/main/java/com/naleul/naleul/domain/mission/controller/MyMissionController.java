package com.naleul.naleul.domain.mission.controller;

import com.naleul.naleul.domain.mission.dto.response.MyMissionResponse;
import com.naleul.naleul.domain.mission.service.MyMissionService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
public class MyMissionController {

    private final MyMissionService myMissionService;

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<MyMissionResponse>> getMyMissions(
            @AuthenticationPrincipal Long userId
    ) {
        MyMissionResponse response = myMissionService.getMyMissions(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.MISSION_FOUND, response));
    }
}