package com.naleul.naleul.domain.mission.dto.response;

import com.naleul.naleul.domain.mission.entity.MissionParticipant;
import lombok.Getter;
import lombok.NoArgsConstructor;

// MissionActiveParticipantResponse.java
@Getter
@NoArgsConstructor
public class MissionActiveParticipantResponse {
    private Long missionParticipantId;
    private Long userId;
    private String userName;
    private String introduction;
    private String status; // ACTIVE, COMPLETED, FAILED

    public static MissionActiveParticipantResponse from(MissionParticipant participant) {
        MissionActiveParticipantResponse dto = new MissionActiveParticipantResponse();
        dto.missionParticipantId = participant.getMissionParticipantId();
        dto.userId = participant.getUser().getUserId();
        dto.userName = participant.getUser().getUserName();
        dto.introduction = participant.getIntroduction();
        dto.status = participant.getStatus().name();
        return dto;
    }
}
