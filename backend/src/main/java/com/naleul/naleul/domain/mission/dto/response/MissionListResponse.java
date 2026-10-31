package com.naleul.naleul.domain.mission.dto.response;

import com.naleul.naleul.domain.mission.entity.Mission;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class MissionListResponse {

    private Long missionId;
    private String missionTitle;
    private Boolean isPrivate;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private String missionDuration;
    private LocalDate recruitStartDate;
    private LocalDate recruitEndDate;
    private String missionStatus;
    private Boolean isLike;

    // ✅ 추가
    private String category;
    private String categoryItem;
    private String customCategoryName;
    private String customItemName;

    public static MissionListResponse from(Mission mission, boolean isLike) {
        return MissionListResponse.builder()
                .missionId(mission.getMissionId())
                .missionTitle(mission.getMissionTitle())
                .isPrivate(mission.getIsPrivate())
                .maxParticipants(mission.getMaxParticipants())
                .currentParticipants(mission.getParticipants().size())
                .missionDuration(mission.getMissionDuration().name())
                .recruitStartDate(mission.getRecruitStartDate())
                .recruitEndDate(mission.getRecruitEndDate())
                .missionStatus(mission.getMissionStatus().name())
                .isLike(isLike)
                // ✅ 추가
                .category(mission.getCategory() != null ? mission.getCategory().name() : null)
                .categoryItem(mission.getCategoryItem() != null ? mission.getCategoryItem().name() : null)
                .customCategoryName(mission.getCustomCategoryName())
                .customItemName(mission.getCustomItemName())
                .build();
    }
}