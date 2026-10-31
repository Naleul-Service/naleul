package com.naleul.naleul.domain.mission.dto.response;

import com.naleul.naleul.domain.mission.entity.MissionTaskComplete;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class MissionTaskCompleteResponse {

    private Long missionTaskCompleteId;
    private Long taskId;
    private Long userId;
    private String userName;
    private String imageUrl;
    private String comment;
    private LocalDate completeDate;

    public static MissionTaskCompleteResponse from(MissionTaskComplete complete) {
        return MissionTaskCompleteResponse.builder()
                .missionTaskCompleteId(complete.getMissionTaskCompleteId())
                .taskId(complete.getTask().getTaskId())
                .userId(complete.getUser().getUserId())
                .userName(complete.getUser().getUserName())
                .imageUrl(complete.getImageUrl())
                .comment(complete.getComment())
                .completeDate(complete.getCompleteDate())
                .build();
    }
}