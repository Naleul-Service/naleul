package com.naleul.naleul.domain.mission.service;

import com.naleul.naleul.domain.mission.entity.Mission;
import com.naleul.naleul.domain.mission.entity.MissionPoke;
import com.naleul.naleul.domain.mission.enums.MissionParticipantStatus;
import com.naleul.naleul.domain.mission.repository.MissionPokeRepository;
import com.naleul.naleul.domain.mission.repository.MissionRepository;
import com.naleul.naleul.domain.notification.entity.NotificationType;
import com.naleul.naleul.domain.notification.service.NotificationService;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.repository.UserRepository;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class MissionPokeService {

    private final MissionRepository missionRepository;
    private final UserFinder userFinder;
    private final TaskRepository taskRepository;
    private final MissionPokeRepository missionPokeRepository;
    private final NotificationService notificationService;

    public void poke(Long senderId, Long missionId, Long targetUserId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new CustomException(ErrorCode.MISSION_NOT_FOUND));

        User sender = userFinder.findUser(senderId);

        User targetUser = mission.getParticipants().stream()
                .filter(p -> p.getUser().getUserId().equals(targetUserId)
                        && p.getStatus() == MissionParticipantStatus.ACTIVE)
                .map(p -> p.getUser())
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.PARTICIPANT_NOT_FOUND));

        Optional<Task> todayTask = taskRepository.findMissionTaskByUserIdAndMissionIdAndDate(
                targetUserId, missionId, LocalDate.now());
        if (todayTask.isPresent() && todayTask.get().getTaskStatus() == TaskStatus.COMPLETED) {
            throw new CustomException(ErrorCode.TASK_ALREADY_COMPLETED);
        }

        // ✅ 하루 1회 제한 — 애플리케이션 레벨 1차 체크
        LocalDate today = LocalDate.now();
        boolean alreadyPoked = missionPokeRepository
                .existsByMission_MissionIdAndSender_UserIdAndTargetUser_UserIdAndPokeDate(
                        missionId, senderId, targetUserId, today);
        if (alreadyPoked) {
            throw new CustomException(ErrorCode.MISSION_ALREADY_POKED_TODAY);
        }

        try {
            // ✅ DB 유니크 제약 — 동시 요청에 대한 2차 방어선
            missionPokeRepository.save(MissionPoke.create(mission, sender, targetUser, today));
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.MISSION_ALREADY_POKED_TODAY);
        }

        notificationService.notify(targetUser, NotificationType.POKE_RECEIVED, mission.getMissionId());
    }
}