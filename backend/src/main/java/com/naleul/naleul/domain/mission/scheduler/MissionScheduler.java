package com.naleul.naleul.domain.mission.scheduler;

import com.naleul.naleul.domain.mission.entity.Mission;
import com.naleul.naleul.domain.mission.entity.MissionParticipant;
import com.naleul.naleul.domain.mission.enums.MissionParticipantStatus;
import com.naleul.naleul.domain.mission.enums.MissionStatus;
import com.naleul.naleul.domain.mission.repository.MissionBookmarkRepository;
import com.naleul.naleul.domain.mission.repository.MissionParticipantRepository;
import com.naleul.naleul.domain.mission.repository.MissionRepository;
import com.naleul.naleul.domain.mission.service.MissionBadgeService;
import com.naleul.naleul.domain.mission.service.MissionParticipantService;
import com.naleul.naleul.domain.mission.util.MissionPuzzleCalculator;
import com.naleul.naleul.domain.notification.entity.NotificationType;
import com.naleul.naleul.domain.notification.service.NotificationService;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserExpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MissionScheduler {

    private final MissionRepository missionRepository;
    private final MissionParticipantRepository missionParticipantRepository;
    private final MissionParticipantService missionParticipantService;
    private final MissionBadgeService missionBadgeService;
    private final UserExpService userExpService;
    private final TaskRepository taskRepository;
    private final NotificationService notificationService;
    private final MissionBookmarkRepository missionBookmarkRepository;

    // 오늘 모집이 시작되는 미션 → 찜한 유저에게 알림
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void notifyWishlistRecruitStarted() {
        LocalDate today = LocalDate.now();
        List<Mission> startedMissions = missionRepository
                .findByMissionStatusAndRecruitStartDate(MissionStatus.RECRUITING, today);

        for (Mission mission : startedMissions) {
            List<User> bookmarkers = missionBookmarkRepository.findUsersByMissionId(mission.getMissionId());
            bookmarkers.forEach(u -> notificationService.notify(
                    u, NotificationType.WISHLIST_RECRUIT_STARTED, mission.getMissionId()
            ));
        }
    }

    // 모집마감 하루 전: 찜한 유저 + (승인 대기 있는 경우) 방장 + 승인/거절 결과 일괄 발송
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void notifyRecruitDeadlineD1() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Mission> deadlineSoonMissions = missionRepository
                .findByMissionStatusAndRecruitEndDate(MissionStatus.RECRUITING, tomorrow);

        for (Mission mission : deadlineSoonMissions) {
            // 찜한 유저들
            List<User> bookmarkers = missionBookmarkRepository.findUsersByMissionId(mission.getMissionId());
            bookmarkers.forEach(u -> notificationService.notify(
                    u, NotificationType.WISHLIST_RECRUIT_DEADLINE_D1, mission.getMissionId()
            ));

            // 승인 대기중인 신청이 있으면 방장한테도
            long pendingCount = missionParticipantRepository
                    .countByMissionMissionIdAndStatus(mission.getMissionId(), MissionParticipantStatus.PENDING);
            if (pendingCount > 0) {
                notificationService.notify(
                        mission.getHost(), NotificationType.RECRUIT_DEADLINE_D1_FOR_HOST, mission.getMissionId()
                );
            }

            // ✅ 추가 — 그동안 모아둔 승인/거절 결과를 이 시점에 일괄 발송
            missionParticipantService.notifyPendingApplicationResults(mission);
        }
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void updateMissionStatus() {
        LocalDate today = LocalDate.now();

        List<Mission> missions = missionRepository.findByMissionStatusAndRecruitEndDateBefore(
                MissionStatus.RECRUITING, today
        );

        for (Mission mission : missions) {
            long activeCount = missionParticipantRepository
                    .countByMissionMissionIdAndStatus(
                            mission.getMissionId(),
                            MissionParticipantStatus.ACTIVE
                    );

            if (activeCount >= 2) {
                mission.startMission();
                missionParticipantService.createTasksForActiveParticipants(mission);
                log.info("미션 진행 시작: missionId={}, 참여자={}명", mission.getMissionId(), activeCount);

                // 알림: ACTIVE 참여자 전원에게 미션 시작
                notifyActiveParticipants(mission, NotificationType.MISSION_STARTED);
            } else {
                taskRepository.deleteAllByMissionParticipantMissionMissionId(mission.getMissionId());
                mission.delete();
                log.info("미션 취소: missionId={}, 참여자={}명 (2명 미만)", mission.getMissionId(), activeCount);
            }
        }
    }

    // 진행 기간이 끝난 IN_PROGRESS 미션을 찾아 참여자별 성공/실패를 자동 확정
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void finalizeCompletedMissions() {
        LocalDate today = LocalDate.now();

        List<Mission> inProgressMissions = missionRepository.findByMissionStatus(MissionStatus.IN_PROGRESS);

        for (Mission mission : inProgressMissions) {
            LocalDate progressStart = mission.getRecruitEndDate().plusDays(1);
            LocalDate progressEnd = mission.getMissionDuration().endDate(progressStart);

            if (!progressEnd.isBefore(today)) {
                continue;
            }

            finalizeMission(mission, progressStart, progressEnd);
        }
    }

    private void finalizeMission(Mission mission, LocalDate progressStart, LocalDate progressEnd) {
        List<MissionParticipant> activeParticipants = mission.getParticipants().stream()
                .filter(p -> p.getStatus() == MissionParticipantStatus.ACTIVE)
                .toList();

        for (MissionParticipant participant : activeParticipants) {
            List<Task> tasks = taskRepository.findMissionTasks(
                    mission.getMissionId(), participant.getUser().getUserId(), progressStart, progressEnd
            );
            int totalScheduledDays = tasks.size(); // ✅ 실제 생성된 예정일 수
            int achievedDays = (int) tasks.stream()
                    .filter(t -> t.getTaskStatus() == TaskStatus.COMPLETED)
                    .count();

            if (MissionPuzzleCalculator.isSuccess(achievedDays, totalScheduledDays)) {
                participant.success();
                userExpService.gainMissionSuccessExp(participant.getUser().getUserId());
                missionBadgeService.awardBadgesOnMissionSuccess(mission, participant.getUser(), achievedDays, totalScheduledDays);
            } else {
                participant.fail();
            }

            notificationService.notify(participant.getUser(), NotificationType.MISSION_COMPLETED, mission.getMissionId());
        }

        mission.completeMission();
    }

    private void notifyActiveParticipants(Mission mission, NotificationType type) {
        mission.getParticipants().stream()
                .filter(p -> p.getStatus() == MissionParticipantStatus.ACTIVE)
                .forEach(p -> notificationService.notify(p.getUser(), type, mission.getMissionId()));
    }
}