package com.naleul.naleul.domain.mission.service;

import com.naleul.naleul.domain.mission.dto.response.MissionDailyStatusResponse;
import com.naleul.naleul.domain.mission.entity.Mission;
import com.naleul.naleul.domain.mission.entity.MissionParticipant;
import com.naleul.naleul.domain.mission.entity.MissionTaskComplete;
import com.naleul.naleul.domain.mission.enums.MissionParticipantStatus;
import com.naleul.naleul.domain.mission.enums.MissionStatus;
import com.naleul.naleul.domain.mission.repository.MissionRepository;
import com.naleul.naleul.domain.mission.repository.MissionTaskCompleteRepository;
import com.naleul.naleul.domain.mission.repository.MissionTaskLikeRepository;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionDailyStatusService {

    private final MissionRepository missionRepository;
    private final TaskRepository taskRepository;
    private final MissionTaskCompleteRepository missionTaskCompleteRepository;
    private final MissionTaskLikeRepository missionTaskLikeRepository;

    public MissionDailyStatusResponse getDailyStatus(Long userId, Long missionId) {
        Mission mission = missionRepository.findDetailById(missionId)
                .orElseThrow(() -> new CustomException(ErrorCode.MISSION_NOT_FOUND));

        // ✅ 변경 — 미션이 COMPLETED면 "마지막 진행일" 기준, 아니면 "오늘" 기준
        LocalDate referenceDate = resolveReferenceDate(mission);

        // ✅ 변경 — ACTIVE뿐 아니라 완료 후 확정된 참여자(COMPLETED/FAILED)도 포함
        //    (PENDING, REJECTED는 여전히 제외 — 신청 대기중/거절된 사람은 기록 화면에 안 나와야 하므로)git
        List<MissionParticipant> targetParticipants = mission.getParticipants().stream()
                .filter(this::isDisplayableParticipant)
                .collect(Collectors.toList());

        List<MissionDailyStatusResponse.ParticipantDailyStatus> statuses = targetParticipants.stream()
                .map(participant -> buildParticipantStatus(participant, referenceDate, userId))
                .collect(Collectors.toList());

        return MissionDailyStatusResponse.builder()
                .missionId(missionId)
                .missionTitle(mission.getMissionTitle())
                .categoryItem(mission.getCategoryItem() != null ? mission.getCategoryItem().name() : null)
                .participants(statuses)
                .build();
    }

    // ✅ 추가 — 오타 수정 포함 (isDisplayablePariticipant → isDisplayableParticipant)
    private boolean isDisplayableParticipant(MissionParticipant p) {
        MissionParticipantStatus status = p.getStatus();
        return status == MissionParticipantStatus.ACTIVE
                || status == MissionParticipantStatus.COMPLETED
                || status == MissionParticipantStatus.FAILED;
    }

    // ✅ 추가 — 미션 상태 기준으로 "오늘" 대신 쓸 기준 날짜 결정
    //    MissionScheduler.finalizeMission()의 계산식(recruitEndDate+1 ~ duration.endDate)과 동일하게 맞춤
    private LocalDate resolveReferenceDate(Mission mission) {
        if (mission.getMissionStatus() == MissionStatus.COMPLETED) {
            LocalDate progressStart = mission.getRecruitEndDate().plusDays(1);
            return mission.getMissionDuration().endDate(progressStart);
        }
        return LocalDate.now();
    }

    private MissionDailyStatusResponse.ParticipantDailyStatus buildParticipantStatus(
            MissionParticipant participant, LocalDate referenceDate, Long myUserId) {

        Long participantId = participant.getMissionParticipantId();
        Long participantUserId = participant.getUser().getUserId();

        // ✅ 변경 — "오늘"이 아니라 referenceDate 기준으로 Task 조회
        Optional<Task> targetTask = taskRepository.findTodayMissionTask(participantId, referenceDate);

        boolean isCompleted = targetTask
                .map(t -> t.getTaskStatus() == TaskStatus.COMPLETED)
                .orElse(false);

        String imageUrl = null;
        String comment = null;
        Long missionTaskCompleteId = null;
        int likeCount = 0;
        boolean likedByMe = false;

        if (isCompleted && targetTask.isPresent()) {
            Optional<MissionTaskComplete> complete = missionTaskCompleteRepository
                    .findByTaskTaskId(targetTask.get().getTaskId());

            if (complete.isPresent()) {
                MissionTaskComplete mtc = complete.get();
                imageUrl = mtc.getImageUrl();
                comment = mtc.getComment();
                missionTaskCompleteId = mtc.getMissionTaskCompleteId();
                likeCount = mtc.getLikeCount();
                likedByMe = missionTaskLikeRepository
                        .existsByMissionTaskComplete_MissionTaskCompleteIdAndUser_UserId(
                                mtc.getMissionTaskCompleteId(), myUserId);
            }
        }

        return MissionDailyStatusResponse.ParticipantDailyStatus.builder()
                .userId(participantUserId)
                .userName(participant.getUser().getUserName())
                .participantStatus(participant.getStatus().name())
                .completed(isCompleted)
                .imageUrl(imageUrl)
                .comment(comment)
                .missionTaskCompleteId(missionTaskCompleteId)
                .likeCount(likeCount)
                .likedByMe(likedByMe)
                .me(participantUserId.equals(myUserId))
                .build();
    }
}