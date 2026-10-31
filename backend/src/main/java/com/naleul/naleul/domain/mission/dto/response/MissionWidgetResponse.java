// dto/response/MissionWidgetResponse.java
package com.naleul.naleul.domain.mission.dto.response;

import com.naleul.naleul.domain.mission.enums.MissionCategoryItem;
import com.naleul.naleul.domain.mission.enums.MissionStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MissionWidgetResponse {
    private Long missionId;
    private String missionTitle;
    private MissionCategoryItem categoryItem;
    private MissionStatus missionStatus;
    private double achievementRate; // 0~100
    private long daysLeft;
    private List<WidgetDayRecord> weeklyRecords;

    @Getter
    @Builder
    public static class WidgetDayRecord {
        private String dayOfWeek; // MON, TUE...
        private java.time.LocalDate date;
        private boolean completed;
    }
}