package com.naleul.naleul.domain.mission.dto.response;

import com.naleul.naleul.domain.mission.entity.Mission;
import com.naleul.naleul.domain.mission.entity.MissionParticipant;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class TeamAchievementResponse {

    private Long missionId;
    private String missionTitle;
    private String missionStatus;         // COMPLETED
    private LocalDate progressStartDate;
    private LocalDate progressEndDate;
    private double partyAchievementRate;  // 파티 달성률 (%)
    private List<MemberResult> memberResults; // 참여자별 성공/실패
    private int achievedDays;      // ✅ 추가
    private int requiredDays;      // ✅ 추가
    private int puzzlePieces;      // ✅ 추가 (0~5)
    private boolean isSuccess;     // ✅ 추가

    @Getter
    @Builder
    public static class MemberResult {
        private Long userId;
        private String userName;
        private String status;  // COMPLETED, FAILED

        public static MemberResult from(MissionParticipant participant) {
            return MemberResult.builder()
                    .userId(participant.getUser().getUserId())
                    .userName(participant.getUser().getUserName())
                    .status(participant.getStatus().name())
                    .build();
        }
    }
}