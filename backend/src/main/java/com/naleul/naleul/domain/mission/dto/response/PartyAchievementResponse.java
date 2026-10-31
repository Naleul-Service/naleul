package com.naleul.naleul.domain.mission.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class PartyAchievementResponse {

    private Long missionId;
    private int totalParticipants;       // 전체 참여자 수
    private int requiredCount;           // 달성 기준 인원 (절반 이상, 홀수면 올림)
    private double achievementRate;      // 파티 달성률 (%)
    private double progressRate;         // 진행률 (%)
    private LocalDate startDate;
    private LocalDate endDate;
    private List<PartyGrassCell> grassCells;

    @Getter
    @Builder
    public static class PartyGrassCell {
        private LocalDate date;
        private int completedCount;  // 해당 날짜에 완료한 인원 수
        private boolean partyAchieved; // 파티 달성 여부 (기준 인원 이상)
    }
}