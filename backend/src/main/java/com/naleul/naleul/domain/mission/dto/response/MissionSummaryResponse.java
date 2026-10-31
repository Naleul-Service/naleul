package com.naleul.naleul.domain.mission.dto.response;

import com.naleul.naleul.domain.mission.entity.Mission;
import com.naleul.naleul.domain.mission.entity.MissionParticipant;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class MissionSummaryResponse {

    private Long missionId;
    private String missionTitle;
    private String missionStatus;
    private String participantStatus;
    private LocalDate recruitEndDate;
    private LocalDate progressStartDate;
    private LocalDate progressEndDate;
    private Integer maxParticipants;
    private Integer currentParticipants;

    // ✅ 추가
    private String category;
    private String categoryItem;
    private String customCategoryName;
    private String customItemName;

    public static MissionSummaryResponse from(Mission mission) {
        return MissionSummaryResponse.builder()
                .missionId(mission.getMissionId())
                .missionTitle(mission.getMissionTitle())
                .missionStatus(mission.getMissionStatus().name())
                .participantStatus(null)
                .recruitEndDate(mission.getRecruitEndDate())
                .progressStartDate(mission.getRecruitEndDate().plusDays(1))
                .progressEndDate(calculateEndDate(mission))
                .maxParticipants(mission.getMaxParticipants())
                .currentParticipants(mission.getParticipants().size())
                // ✅ 추가
                .category(mission.getCategory() != null ? mission.getCategory().name() : null)
                .categoryItem(mission.getCategoryItem() != null ? mission.getCategoryItem().name() : null)
                .customCategoryName(mission.getCustomCategoryName())
                .customItemName(mission.getCustomItemName())
                .build();
    }

    public static MissionSummaryResponse fromParticipant(MissionParticipant participant) {
        Mission mission = participant.getMission();
        return MissionSummaryResponse.builder()
                .missionId(mission.getMissionId())
                .missionTitle(mission.getMissionTitle())
                .missionStatus(mission.getMissionStatus().name())
                .participantStatus(participant.getStatus().name())
                .recruitEndDate(mission.getRecruitEndDate())
                .progressStartDate(mission.getRecruitEndDate().plusDays(1))
                .progressEndDate(calculateEndDate(mission))
                .maxParticipants(mission.getMaxParticipants())
                .currentParticipants(mission.getParticipants().size())
                // ✅ 추가
                .category(mission.getCategory() != null ? mission.getCategory().name() : null)
                .categoryItem(mission.getCategoryItem() != null ? mission.getCategoryItem().name() : null)
                .customCategoryName(mission.getCustomCategoryName())
                .customItemName(mission.getCustomItemName())
                .build();
    }

    private static LocalDate calculateEndDate(Mission mission) {
        LocalDate start = mission.getRecruitEndDate().plusDays(1);
        return switch (mission.getMissionDuration()) {
            case ONE_WEEK     -> start.plusWeeks(1).minusDays(1);
            case TWO_WEEKS    -> start.plusWeeks(2).minusDays(1);
            case THREE_WEEKS  -> start.plusWeeks(3).minusDays(1);
            case ONE_MONTH    -> start.plusMonths(1).minusDays(1);
            case TWO_MONTHS   -> start.plusMonths(2).minusDays(1);
            case THREE_MONTHS -> start.plusMonths(3).minusDays(1);
        };
    }
}