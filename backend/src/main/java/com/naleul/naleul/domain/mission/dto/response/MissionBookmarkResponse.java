package com.naleul.naleul.domain.mission.dto.response;

import com.naleul.naleul.domain.mission.entity.MissionBookmark;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class MissionBookmarkResponse {

    private Long missionBookmarkId;
    private Long missionId;
    private String missionTitle;
    private Boolean isPrivate;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private LocalDate recruitStartDate;
    private LocalDate recruitEndDate;
    private String missionStatus;

    public static MissionBookmarkResponse from(MissionBookmark bookmark) {
        return MissionBookmarkResponse.builder()
                .missionBookmarkId(bookmark.getMissionBookmarkId())
                .missionId(bookmark.getMission().getMissionId())
                .missionTitle(bookmark.getMission().getMissionTitle())
                .isPrivate(bookmark.getMission().getIsPrivate())
                .maxParticipants(bookmark.getMission().getMaxParticipants())
                .currentParticipants(bookmark.getMission().getParticipants().size())
                .recruitStartDate(bookmark.getMission().getRecruitStartDate())
                .recruitEndDate(bookmark.getMission().getRecruitEndDate())
                .missionStatus(bookmark.getMission().getMissionStatus().name())
                .build();
    }
}