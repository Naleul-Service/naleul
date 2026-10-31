package com.naleul.naleul.domain.mission.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;

@Entity
@Table(name = "mission_repeat_days")
@Getter
@NoArgsConstructor
public class MissionRepeatDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long missionRepeatDayId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DayOfWeek dayOfWeek;

    @Builder
    public MissionRepeatDay(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    void setMission(Mission mission) {
        this.mission = mission;
    }
}