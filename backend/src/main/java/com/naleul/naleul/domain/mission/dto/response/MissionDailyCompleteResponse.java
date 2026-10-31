package com.naleul.naleul.domain.mission.dto.response;

import com.naleul.naleul.domain.mission.entity.MissionTaskComplete;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class MissionDailyCompleteResponse {

    private Long missionTaskCompleteId;
    private Long userId;
    private String userName;
    private String imageUrl;
    private String comment;
    private LocalDate completeDate;
    private int likeCount;        // 하트 개수
    private boolean likedByMe;    // 내가 좋아요 눌렀는지

    public static MissionDailyCompleteResponse of(MissionTaskComplete complete, boolean likedByMe) {
        return MissionDailyCompleteResponse.builder()
                .missionTaskCompleteId(complete.getMissionTaskCompleteId())
                .userId(complete.getUser().getUserId())
                .userName(complete.getUser().getUserName())
                .imageUrl(complete.getImageUrl())
                .comment(complete.getComment())
                .completeDate(complete.getCompleteDate())
                .likeCount(complete.getLikeCount())
                .likedByMe(likedByMe)
                .build();
    }
}