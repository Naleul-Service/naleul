package com.naleul.naleul.domain.mission.entity;

import com.naleul.naleul.domain.mission.enums.*;
import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "missions")
@Getter
@NoArgsConstructor
public class Mission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long missionId;

    @Column(nullable = false, length = 100)
    private String missionTitle;

    @Column(nullable = false)
    private Integer maxParticipants;

    @Column(nullable = false)
    private Boolean isPrivate;

    @Column
    private String missionPassword;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MissionCategory category;

    @Enumerated(EnumType.STRING)
    private MissionCategoryItem categoryItem; // category == ETC 이면 null

    @Column(length = 10)
    private String customCategoryName; // category == ETC 일 때만 값 존재

    @Column(length = 10)
    private String customItemName; // categoryItem == ETC 일 때만 값 존재

    @Column(nullable = false, length = 500)
    private String missionCondition;

    @Column(nullable = false)
    private LocalDate recruitStartDate;

    @Column(nullable = false)
    private LocalDate recruitEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MissionDuration missionDuration;

    @Column(length = 500)
    private String successCondition; // 기존 필수 → 선택으로 변경 (nullable=false 제거)

    @Column(length = 500)
    private String failCondition; // 기존 필수 → 선택으로 변경 (nullable=false 제거)

    @Column(length = 500)
    private String participationCondition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) // 기존 선택 → 필수로 변경
    private RepeatDayType repeatDayType;

    @OneToMany(mappedBy = "mission", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MissionParticipant> participants = new LinkedHashSet<>();

    @OneToMany(mappedBy = "mission", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MissionRepeatDay> repeatDays = new LinkedHashSet<>();

    @Embedded
    private MissionTimeRange timeRange;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MissionStatus missionStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User host;

    public void addParticipant(MissionParticipant participant) {
        this.participants.add(participant);
    }

    public void startMission() {
        this.missionStatus = MissionStatus.IN_PROGRESS;
    }

    @Builder
    public Mission(String missionTitle, Integer maxParticipants, Boolean isPrivate,
                   String missionPassword, MissionCategory category, MissionCategoryItem categoryItem,
                   String customCategoryName, String customItemName, String missionCondition,
                   LocalDate recruitStartDate, LocalDate recruitEndDate,
                   MissionDuration missionDuration, String successCondition,
                   String failCondition, String participationCondition,
                   RepeatDayType repeatDayType, MissionTimeRange timeRange, User host) {
        this.missionTitle = missionTitle;
        this.maxParticipants = maxParticipants;
        this.isPrivate = isPrivate;
        this.missionPassword = Boolean.TRUE.equals(isPrivate) ? missionPassword : null;
        this.category = category;
        this.categoryItem = categoryItem;
        this.customCategoryName = customCategoryName;
        this.customItemName = customItemName;
        this.missionCondition = missionCondition;
        this.recruitStartDate = recruitStartDate;
        this.recruitEndDate = recruitEndDate;
        this.missionDuration = missionDuration;
        this.successCondition = successCondition;
        this.failCondition = failCondition;
        this.participationCondition = participationCondition;
        this.repeatDayType = repeatDayType;
        this.timeRange = timeRange;
        this.missionStatus = MissionStatus.RECRUITING;
        this.host = host;
    }

    public void addRepeatDay(MissionRepeatDay repeatDay) {
        this.repeatDays.add(repeatDay);
        repeatDay.setMission(this);
    }

    public void completeMission() {
        this.missionStatus = MissionStatus.COMPLETED;
    }

    public void update(String missionTitle, Integer maxParticipants, Boolean isPrivate,
                       String missionPassword, MissionCategory category, MissionCategoryItem categoryItem,
                       String customCategoryName, String customItemName, String missionCondition,
                       LocalDate recruitStartDate, LocalDate recruitEndDate,
                       MissionDuration missionDuration, String successCondition,
                       String failCondition, String participationCondition,
                       RepeatDayType repeatDayType, MissionTimeRange timeRange) {

        this.missionTitle = missionTitle;
        this.maxParticipants = maxParticipants;
        this.isPrivate = isPrivate;
        this.missionPassword = Boolean.TRUE.equals(isPrivate) ? missionPassword : null;
        this.category = category;
        this.categoryItem = categoryItem;
        this.customCategoryName = customCategoryName;
        this.customItemName = customItemName;
        this.missionCondition = missionCondition;
        this.recruitStartDate = recruitStartDate;
        this.recruitEndDate = recruitEndDate;
        this.missionDuration = missionDuration;
        this.successCondition = successCondition;
        this.failCondition = failCondition;
        this.participationCondition = participationCondition;
        this.repeatDayType = repeatDayType;
        this.timeRange = timeRange;
    }

    public void updateRepeatDays(List<DayOfWeek> newDays) {
        this.repeatDays.clear();
        if (newDays != null) {
            newDays.forEach(day ->
                    this.repeatDays.add(MissionRepeatDay.builder()
                            .dayOfWeek(day)
                            .build())
            );
        }
    }

    public void delete() {
        this.missionStatus = MissionStatus.DELETED;
    }
}