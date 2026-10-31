package com.naleul.naleul.domain.mission.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class MissionDailyDetailResponse {

    private LocalDate date;
    private Double achievementRate; // 0~100

    private List<ParticipantDaily> participants;

    @Getter
    @Builder
    public static class ParticipantDaily {
        private Long userId;
        private String userName;
        private boolean completed;
        private String imageUrl; // 미인증이면 null
        private String comment;  // 미인증이면 null
    }
}