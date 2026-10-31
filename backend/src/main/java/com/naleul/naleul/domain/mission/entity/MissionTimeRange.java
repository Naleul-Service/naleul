package com.naleul.naleul.domain.mission.entity;

import jakarta.persistence.Embeddable;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Embeddable
@Getter
@NoArgsConstructor
public class MissionTimeRange {

    private LocalTime startTime;
    private LocalTime endTime;

    @Builder
    public MissionTimeRange(LocalTime startTime, LocalTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }
}