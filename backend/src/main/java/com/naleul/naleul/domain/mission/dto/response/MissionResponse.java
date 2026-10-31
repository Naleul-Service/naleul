package com.naleul.naleul.domain.mission.dto.response;

import com.naleul.naleul.domain.mission.entity.Mission;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class MissionResponse {

    private Long id;
    private String title;
    private Integer maxParticipants;
    private Boolean isPrivate;
    private String category;
    private String categoryItem;
    private String customCategoryName;
    private String customItemName;
    private String missionCondition;
    private LocalDate recruitStartDate;
    private LocalDate recruitEndDate;
    private String duration;
    private String successCondition;
    private String failCondition;
    private String participationCondition;
    private String repeatDayType;
    private List<String> repeatDays;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private Long hostId;
    private LocalDateTime createdAt;

    public static MissionResponse from(Mission mission) {
        return MissionResponse.builder()
                .id(mission.getMissionId())
                .title(mission.getMissionTitle())
                .maxParticipants(mission.getMaxParticipants())
                .isPrivate(mission.getIsPrivate())
                .category(mission.getCategory().name())
                .categoryItem(mission.getCategoryItem() != null ? mission.getCategoryItem().name() : null)
                .customCategoryName(mission.getCustomCategoryName())
                .customItemName(mission.getCustomItemName())
                .missionCondition(mission.getMissionCondition())
                .recruitStartDate(mission.getRecruitStartDate())
                .recruitEndDate(mission.getRecruitEndDate())
                .duration(mission.getMissionDuration().name())
                .successCondition(mission.getSuccessCondition())
                .failCondition(mission.getFailCondition())
                .participationCondition(mission.getParticipationCondition())
                .repeatDayType(mission.getRepeatDayType() != null ? mission.getRepeatDayType().name() : null)
                .repeatDays(mission.getRepeatDays().stream()
                        .map(d -> d.getDayOfWeek().name())
                        .collect(Collectors.toList()))
                .startTime(mission.getTimeRange() != null ? mission.getTimeRange().getStartTime() : null)
                .endTime(mission.getTimeRange() != null ? mission.getTimeRange().getEndTime() : null)
                .status(mission.getMissionStatus().name())
                .hostId(mission.getHost().getUserId())
                .build();
    }
}