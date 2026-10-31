package com.naleul.naleul.domain.mission.dto.response;

import com.naleul.naleul.domain.mission.entity.MissionParticipant;
import com.naleul.naleul.domain.task.entity.Task;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class PersonalAchievementResponse {

    private Long missionId;
    private String missionTitle;
    private String participantStatus;
    private LocalDate progressStartDate;
    private LocalDate progressEndDate;
    private String firstImageUrl;
    private String lastImageUrl;
    private int totalTaskCount;
    private int completedTaskCount;
    private double taskAchievementRate;
    private List<CompletedTaskInfo> completedTasks;
    private int achievedDays;
    private int requiredDays;
    private int puzzlePieces;
    private boolean isSuccess;
    private String categoryItem;   // ✅ 추가 — MissionCategoryItem.name(), ETC 대분류면 null

    @Getter
    @Builder
    public static class CompletedTaskInfo {
        private Long taskId;
        private LocalDate date;
        private String imageUrl;
        private String comment;

        public static CompletedTaskInfo from(Task task, String imageUrl, String comment) {
            return CompletedTaskInfo.builder()
                    .taskId(task.getTaskId())
                    .date(task.getRoutineDate())
                    .imageUrl(imageUrl)
                    .comment(comment)
                    .build();
        }
    }
}