package com.naleul.naleul.domain.mission.dto.response;

import com.naleul.naleul.domain.mission.entity.MissionParticipant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PendingParticipantResponse {

    private Long missionParticipantId;
    private Long userId;
    private String userName;
    private String introduction;
    private String status;

    public static PendingParticipantResponse from(MissionParticipant participant) {
        return PendingParticipantResponse.builder()
                .missionParticipantId(participant.getMissionParticipantId())
                .userId(participant.getUser().getUserId())
                .userName(participant.getUser().getUserName())
                .introduction(participant.getIntroduction())
                .status(participant.getStatus().name())
                .build();
    }
}