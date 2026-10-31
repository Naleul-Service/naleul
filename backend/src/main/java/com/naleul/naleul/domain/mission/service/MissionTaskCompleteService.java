package com.naleul.naleul.domain.mission.service;

import com.naleul.naleul.domain.actualTask.entity.TaskActual;
import com.naleul.naleul.domain.actualTask.repository.TaskActualRepository;
import com.naleul.naleul.domain.mission.dto.request.MissionTaskCompleteRequest;
import com.naleul.naleul.domain.mission.dto.response.MissionTaskCompleteResponse;
import com.naleul.naleul.domain.mission.dto.response.TodayMissionTaskResponse;
import com.naleul.naleul.domain.mission.entity.Mission;
import com.naleul.naleul.domain.mission.entity.MissionParticipant;
import com.naleul.naleul.domain.mission.entity.MissionTaskComplete;
import com.naleul.naleul.domain.mission.enums.MissionParticipantStatus;
import com.naleul.naleul.domain.mission.repository.MissionTaskCompleteRepository;
import com.naleul.naleul.domain.mission.util.MissionPuzzleCalculator;
import com.naleul.naleul.domain.notification.entity.NotificationType;
import com.naleul.naleul.domain.notification.service.NotificationService;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.repository.UserRepository;
import com.naleul.naleul.domain.user.service.UserExpService;
import com.naleul.naleul.domain.user.service.UserFinder;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MissionTaskCompleteService {

    private final TaskRepository taskRepository;
    private final UserFinder userFinder;
    private final MissionTaskCompleteRepository missionTaskCompleteRepository;
    private final UserExpService userExpService;
    private final MissionBadgeService missionBadgeService;
    private final NotificationService notificationService;
    private final MissionParticipantService missionParticipantService;

    public MissionTaskCompleteResponse completeTask(Long userId, Long taskId,
                                                    MissionTaskCompleteRequest request) {
        User user = userFinder.findUser(userId);
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task를 찾을 수 없습니다."));

        validateMissionTask(task);
        validateTaskOwner(task, userId);
        validateNotAlreadyCompleted(taskId);

        task.completeWithActualTime(request.getActualStartAt(), request.getActualEndAt());

        MissionTaskComplete complete = MissionTaskComplete.create(
                task, user, request.getImageUrl(), request.getComment()
        );
        MissionTaskComplete saved = missionTaskCompleteRepository.save(complete);

        userExpService.gainMissionTaskExp(userId);
        missionBadgeService.awardBadgesOnTaskComplete(user, saved);

        notifyTeammatesOnCertification(task, user);
        notifyPuzzlePiecesIfIncreased(task, user);

        return MissionTaskCompleteResponse.from(saved);
    }

    private void notifyTeammatesOnCertification(Task task, User certifiedUser) {
        Mission mission = task.getMissionParticipant().getMission();

        mission.getParticipants().stream()
                .filter(p -> p.getStatus() == MissionParticipantStatus.ACTIVE)
                .filter(p -> !p.getUser().getUserId().equals(certifiedUser.getUserId()))
                .forEach(p -> notificationService.notify(
                        p.getUser(),
                        NotificationType.MISSION_CERTIFIED,
                        mission.getMissionId(),
                        certifiedUser.getUserName()
                ));
    }

    private void notifyPuzzlePiecesIfIncreased(Task task, User certifiedUser) {
        MissionParticipant participant = task.getMissionParticipant();
        Mission mission = participant.getMission();

        // ✅ 변경 — mission.getMissionDuration().getRequiredAchievedDays()가 삭제됐으므로
        //    "실제 예정일 수(반복요일 반영)"를 직접 구해서 사용
        LocalDate progressStart = mission.getRecruitEndDate().plusDays(1);
        LocalDate progressEnd = mission.getMissionDuration().endDate(progressStart);
        List<LocalDate> scheduledDates = missionParticipantService.resolveScheduledDates(
                mission, progressStart, progressEnd
        );
        int totalScheduledDays = scheduledDates.size();

        // 방금 이 Task가 COMPLETED로 바뀐 직후이므로, 이 값이 "완료 후" 개수
        int achievedDaysAfter = (int) taskRepository.countByMissionParticipant_MissionParticipantIdAndTaskStatus(
                participant.getMissionParticipantId(), TaskStatus.COMPLETED
        );
        int achievedDaysBefore = achievedDaysAfter - 1;

        int piecesBefore = MissionPuzzleCalculator.calculatePieces(achievedDaysBefore, totalScheduledDays);
        int piecesAfter = MissionPuzzleCalculator.calculatePieces(achievedDaysAfter, totalScheduledDays);

        if (piecesAfter > piecesBefore) {
            for (int pieceCount = piecesBefore + 1; pieceCount <= piecesAfter; pieceCount++) {
                notificationService.notify(
                        certifiedUser,
                        NotificationType.MISSION_PUZZLE_ACQUIRED,
                        mission.getMissionId(),
                        mission.getMissionTitle(),
                        pieceCount
                );
            }
        }

        boolean justReachedSuccess = MissionPuzzleCalculator.isSuccess(achievedDaysAfter, totalScheduledDays)
                && !MissionPuzzleCalculator.isSuccess(achievedDaysBefore, totalScheduledDays);

        if (justReachedSuccess) {
            confirmMissionSuccess(mission, participant, certifiedUser, achievedDaysAfter, totalScheduledDays);
        }
    }

    private void confirmMissionSuccess(Mission mission, MissionParticipant participant, User user,
                                       int achievedDays, int totalScheduledDays) {
        participant.success();

        userExpService.gainMissionSuccessExp(user.getUserId());
        missionBadgeService.awardBadgesOnMissionSuccess(mission, user, achievedDays, totalScheduledDays);

        notificationService.notify(
                user,
                NotificationType.MISSION_COMPLETED,
                mission.getMissionId()
        );
    }

    // ===== 이하 기존 메서드들 동일 =====

    @Transactional(readOnly = true)
    public MissionTaskCompleteResponse getCompleteRecord(Long taskId) {
        MissionTaskComplete complete = missionTaskCompleteRepository.findByTaskTaskId(taskId)
                .orElseThrow(() -> new EntityNotFoundException("인증 기록을 찾을 수 없습니다."));

        return MissionTaskCompleteResponse.from(complete);
    }

    @Transactional(readOnly = true)
    public TodayMissionTaskResponse getTodayMissionTask(Long userId, Long missionId) {
        LocalDate today = LocalDate.now();

        Task task = taskRepository
                .findMissionTaskByUserIdAndMissionIdAndDate(userId, missionId, today)
                .orElseThrow(() -> new EntityNotFoundException(
                        "오늘의 미션 Task를 찾을 수 없습니다. (missionId=" + missionId + ", date=" + today + ")"
                ));

        return TodayMissionTaskResponse.from(task);
    }

    private void validateMissionTask(Task task) {
        if (task.getMissionParticipant() == null) {
            throw new IllegalArgumentException("미션 Task만 완료 처리할 수 있습니다.");
        }
    }

    private void validateTaskOwner(Task task, Long userId) {
        if (!task.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 Task만 완료 처리할 수 있습니다.");
        }
    }

    private void validateNotAlreadyCompleted(Long taskId) {
        if (missionTaskCompleteRepository.existsByTaskTaskId(taskId)) {
            throw new IllegalArgumentException("이미 완료 처리된 Task입니다.");
        }
    }
}