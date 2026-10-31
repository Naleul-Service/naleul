package com.naleul.naleul.domain.mission.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class MissionGrassMapResponse {

    private Long missionId;
    private Long userId;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<GrassCell> grassCells; // 날짜별 달성 여부

    @Getter
    @Builder
    public static class GrassCell {
        private LocalDate date;
        private boolean completed; // true = 달성, false = 미달성
    }
}