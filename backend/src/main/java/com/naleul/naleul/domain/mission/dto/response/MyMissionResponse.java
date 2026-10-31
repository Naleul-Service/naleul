package com.naleul.naleul.domain.mission.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MyMissionResponse {

    private List<MissionSummaryResponse> participatingMissions;  // 진행중인 미션 (내가 참여중)
    private List<MissionSummaryResponse> hostingMissions;        // 관리중인 미션 (내가 방장)
    private List<MissionSummaryResponse> bookmarkedMissions;     // 찜한 미션
    private List<MissionSummaryResponse> pendingMissions;        // ✅ 신청한 미션
}