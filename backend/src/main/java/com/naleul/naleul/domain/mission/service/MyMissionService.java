package com.naleul.naleul.domain.mission.service;

import com.naleul.naleul.domain.mission.dto.response.MyMissionResponse;
import com.naleul.naleul.domain.mission.dto.response.MissionSummaryResponse;
import com.naleul.naleul.domain.mission.repository.MissionBookmarkRepository;
import com.naleul.naleul.domain.mission.repository.MissionParticipantRepository;
import com.naleul.naleul.domain.mission.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyMissionService {

    private final MissionRepository missionRepository;
    private final MissionParticipantRepository missionParticipantRepository;
    private final MissionBookmarkRepository missionBookmarkRepository;

    public MyMissionResponse getMyMissions(Long userId) {

        // 1. 진행중인 미션 (내가 참여자인 것, 방장 제외)
        List<MissionSummaryResponse> participatingMissions =
                missionParticipantRepository.findParticipatingMissions(userId)
                        .stream()
                        .map(MissionSummaryResponse::fromParticipant)
                        .collect(Collectors.toList());

        // 2. 관리중인 미션 (내가 방장인 것)
        List<MissionSummaryResponse> hostingMissions =
                missionRepository.findHostingMissions(userId)
                        .stream()
                        .map(MissionSummaryResponse::from)
                        .collect(Collectors.toList());

        // 3. 찜한 미션
        List<MissionSummaryResponse> bookmarkedMissions =
                missionBookmarkRepository.findBookmarkedMissions(userId)
                        .stream()
                        .map(bookmark -> MissionSummaryResponse.from(bookmark.getMission()))
                        .collect(Collectors.toList());

        // ✅ 4. 신청한 미션 (PENDING)
        List<MissionSummaryResponse> pendingMissions =
                missionParticipantRepository.findPendingMissions(userId)
                        .stream()
                        .map(MissionSummaryResponse::fromParticipant)
                        .collect(Collectors.toList());


        return MyMissionResponse.builder()
                .participatingMissions(participatingMissions)
                .hostingMissions(hostingMissions)
                .bookmarkedMissions(bookmarkedMissions)
                .pendingMissions(pendingMissions)           // ✅ 추가
                .build();
    }
}