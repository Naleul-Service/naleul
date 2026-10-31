package com.naleul.naleul.domain.mission.dto.response;

import com.naleul.naleul.domain.mission.entity.MissionParticipant;
import com.naleul.naleul.domain.mission.entity.MissionTaskComplete;
import com.naleul.naleul.domain.mission.enums.MissionParticipantStatus;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MissionDailyStatusResponse {

    private Long missionId;
    private String missionTitle;
    private String categoryItem; // ✅ 추가 — MissionCategoryItem.name(), ETC 대분류면 null
    private List<ParticipantDailyStatus> participants;

    @Getter
    @Builder
    public static class ParticipantDailyStatus {
        private Long userId;
        private String userName;
        private String participantStatus;
        private boolean completed;
        private String imageUrl;
        private String comment;
        private Long missionTaskCompleteId;
        private int likeCount;
        private boolean likedByMe;
        private boolean me;
    }
}