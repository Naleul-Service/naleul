package com.naleul.naleul.domain.mission.dto.response;

import com.naleul.naleul.domain.mission.entity.MissionTaskComplete;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class MissionMonthlyPhotoResponse {

    private int year;
    private int month;
    private List<PhotoCell> photos;

    @Getter
    @Builder
    public static class PhotoCell {
        private LocalDate date;
        private String imageUrl;
        private String comment;
        private int likeCount;

        public static PhotoCell from(MissionTaskComplete complete) {
            return PhotoCell.builder()
                    .date(complete.getCompleteDate())
                    .imageUrl(complete.getImageUrl())
                    .comment(complete.getComment())
                    .likeCount(complete.getLikeCount())
                    .build();
        }
    }
}