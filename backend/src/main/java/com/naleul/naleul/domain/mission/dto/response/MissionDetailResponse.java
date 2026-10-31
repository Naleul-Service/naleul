package com.naleul.naleul.domain.mission.dto.response;

import com.naleul.naleul.domain.mission.entity.Mission;
import com.naleul.naleul.domain.mission.entity.MissionParticipant;
import com.naleul.naleul.domain.mission.enums.MissionParticipantStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class MissionDetailResponse {

    private Long missionId;
    private String missionTitle;
    private Boolean isPrivate;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private String category;
    private String categoryItem;
    private String customCategoryName;
    private String customItemName;
    private String missionCondition;
    private LocalDate recruitStartDate;
    private LocalDate recruitEndDate;
    private String missionDuration;
    private String successCondition;
    private String failCondition;
    private String participationCondition;
    private String repeatDayType;
    private List<String> repeatDays;
    private LocalTime startTime;
    private LocalTime endTime;
    private String missionStatus;

    private Boolean isPendingApplicant;
    private Boolean isBookmarked;
    private String missionPassword; // ✅ 추가 — 방장 본인에게만 노출

    private ParticipantInfo host;
    private List<ParticipantInfo> participants;

    @Getter
    @Builder
    public static class ParticipantInfo {
        private Long userId;
        private String userName;
        private String introduction;

        public static ParticipantInfo from(MissionParticipant participant) {
            return ParticipantInfo.builder()
                    .userId(participant.getUser().getUserId())
                    .userName(participant.getUser().getUserName())
                    .introduction(participant.getIntroduction())
                    .build();
        }
    }

    public static MissionDetailResponse from(Mission mission, Long userId, boolean isBookmarked) {
        boolean isPendingApplicant = mission.getParticipants().stream()
                .anyMatch(p -> p.getUser().getUserId().equals(userId)
                        && p.getStatus() == MissionParticipantStatus.PENDING);

        // ✅ 방장 본인일 때만 비밀번호 노출 (다른 참여자/조회자에게는 null)
        boolean isHost = mission.getHost().getUserId().equals(userId);
        // ✅ 추가 — 진행중 상태면서 내가 ACTIVE 참여자인 경우도 비밀번호 열람 허용
        boolean isActiveParticipant = mission.getParticipants().stream()
                .anyMatch(p -> p.getUser().getUserId().equals(userId)
                        && p.getStatus() == MissionParticipantStatus.ACTIVE);
        String visiblePassword = isHost ? mission.getMissionPassword() : null;

        return MissionDetailResponse.builder()
                .missionId(mission.getMissionId())
                .missionTitle(mission.getMissionTitle())
                .isPrivate(mission.getIsPrivate())
                .maxParticipants(mission.getMaxParticipants())
                .currentParticipants(mission.getParticipants().size())
                .category(mission.getCategory().name())
                .categoryItem(mission.getCategoryItem() != null ? mission.getCategoryItem().name() : null)
                .customCategoryName(mission.getCustomCategoryName())
                .customItemName(mission.getCustomItemName())
                .missionCondition(mission.getMissionCondition())
                .recruitStartDate(mission.getRecruitStartDate())
                .recruitEndDate(mission.getRecruitEndDate())
                .missionDuration(mission.getMissionDuration().name())
                .successCondition(mission.getSuccessCondition())
                .failCondition(mission.getFailCondition())
                .participationCondition(mission.getParticipationCondition())
                .repeatDayType(mission.getRepeatDayType() != null ? mission.getRepeatDayType().name() : null)
                .repeatDays(mission.getRepeatDays().stream()
                        .map(d -> d.getDayOfWeek().name())
                        .collect(Collectors.toList()))
                .startTime(mission.getTimeRange() != null ? mission.getTimeRange().getStartTime() : null)
                .endTime(mission.getTimeRange() != null ? mission.getTimeRange().getEndTime() : null)
                .missionStatus(mission.getMissionStatus().name())
                .isPendingApplicant(isPendingApplicant)
                .isBookmarked(isBookmarked)
                .missionPassword(visiblePassword) // ✅ 추가
                .host(ParticipantInfo.builder()
                        .userId(mission.getHost().getUserId())
                        .userName(mission.getHost().getUserName())
                        .introduction(null)
                        .build())
                .participants(mission.getParticipants().stream()
                        .map(ParticipantInfo::from)
                        .collect(Collectors.toList()))
                .build();
    }
}