package com.naleul.naleul.domain.mission.service;

import com.naleul.naleul.domain.mission.dto.response.PersonalAchievementResponse;
import com.naleul.naleul.domain.mission.dto.response.TeamAchievementResponse;
import com.naleul.naleul.domain.mission.entity.Mission;
import com.naleul.naleul.domain.mission.entity.MissionParticipant;
import com.naleul.naleul.domain.mission.entity.MissionTaskComplete;
import com.naleul.naleul.domain.mission.enums.MissionParticipantStatus;
import com.naleul.naleul.domain.mission.repository.MissionParticipantRepository;
import com.naleul.naleul.domain.mission.repository.MissionTaskCompleteRepository;
import com.naleul.naleul.domain.mission.util.MissionPuzzleCalculator;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionAchievementService {

    private final MissionParticipantRepository missionParticipantRepository;
    private final TaskRepository taskRepository;
    private final MissionTaskCompleteRepository missionTaskCompleteRepository;
    private final MissionParticipantService missionParticipantService; // ✅ 추가 — resolveScheduledDates 재사용을 위해 주입

    // 개인 달성 목록 조회
    public List<PersonalAchievementResponse> getPersonalAchievements(Long userId) {
        List<MissionParticipant> participants =
                missionParticipantRepository.findPersonalAchievements(userId);

        return participants.stream()
                .map(participant -> buildPersonalAchievement(participant, userId))
                .collect(Collectors.toList());
    }

    // 팀 달성 목록 조회
    public List<TeamAchievementResponse> getTeamAchievements(Long userId) {
        List<MissionParticipant> participants =
                missionParticipantRepository.findTeamAchievements(userId);

        return participants.stream()
                .map(participant -> buildTeamAchievement(participant))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PersonalAchievementResponse> getUnconfirmedResults(Long userId) {
        List<MissionParticipant> unconfirmed =
                missionParticipantRepository.findUnconfirmedResults(userId);
        return unconfirmed.stream()
                .map(p -> buildPersonalAchievement(p, userId))
                .collect(Collectors.toList());
    }

    @Transactional
    public void confirmResult(Long userId, Long missionId) {
        MissionParticipant participant = missionParticipantRepository
                .findByMission_MissionIdAndUser_UserId(missionId, userId)
                .orElseThrow(() -> new EntityNotFoundException("참여 기록을 찾을 수 없습니다."));
        participant.confirmResult();
    }

    // ===== private 빌드 메서드 =====

    private PersonalAchievementResponse buildPersonalAchievement(
            MissionParticipant participant, Long userId) {

        Mission mission = participant.getMission();
        Long missionId = mission.getMissionId();

        LocalDate startDate = mission.getRecruitEndDate().plusDays(1);
        LocalDate endDate = mission.getMissionDuration().endDate(startDate);

        List<Task> allTasks = taskRepository.findAllMissionTasks(missionId, userId);
        int totalTaskCount = allTasks.size(); // 이게 곧 "실제 예정일 수" (반복요일 반영됨)
        int completedTaskCount = (int) allTasks.stream()
                .filter(t -> t.getTaskStatus() == TaskStatus.COMPLETED)
                .count();

        double taskAchievementRate = totalTaskCount == 0 ? 0 :
                Math.round((double) completedTaskCount / totalTaskCount * 1000) / 10.0;

        // ✅ requiredDays를 totalTaskCount 기준 80%로 역산해서 응답에 담아줌 (프론트 "n일 필요" 표시용)
        int requiredDays = MissionPuzzleCalculator.getRequiredDays(totalTaskCount);
        int puzzlePieces = MissionPuzzleCalculator.calculatePieces(completedTaskCount, totalTaskCount);
        boolean isSuccess = MissionPuzzleCalculator.isSuccess(completedTaskCount, totalTaskCount);

        List<MissionTaskComplete> completes =
                missionTaskCompleteRepository.findAllByMissionAndUser(missionId, userId);

        String firstImageUrl = completes.isEmpty() ? null : completes.get(0).getImageUrl();
        String lastImageUrl = completes.isEmpty() ? null :
                completes.get(completes.size() - 1).getImageUrl();

        Map<Long, MissionTaskComplete> completeMap = completes.stream()
                .collect(Collectors.toMap(
                        mtc -> mtc.getTask().getTaskId(),
                        mtc -> mtc
                ));

        List<PersonalAchievementResponse.CompletedTaskInfo> completedTasks = allTasks.stream()
                .filter(t -> t.getTaskStatus() == TaskStatus.COMPLETED)
                .map(t -> {
                    MissionTaskComplete mtc = completeMap.get(t.getTaskId());
                    return PersonalAchievementResponse.CompletedTaskInfo.from(
                            t,
                            mtc != null ? mtc.getImageUrl() : null,
                            mtc != null ? mtc.getComment() : null
                    );
                })
                .collect(Collectors.toList());

        return PersonalAchievementResponse.builder()
                .missionId(missionId)
                .missionTitle(mission.getMissionTitle())
                .participantStatus(participant.getStatus().name())
                .progressStartDate(startDate)
                .progressEndDate(endDate)
                .firstImageUrl(firstImageUrl)
                .lastImageUrl(lastImageUrl)
                .totalTaskCount(totalTaskCount)
                .completedTaskCount(completedTaskCount)
                .taskAchievementRate(taskAchievementRate)
                .completedTasks(completedTasks)
                .achievedDays(completedTaskCount)
                .requiredDays(requiredDays)
                .puzzlePieces(puzzlePieces)
                .isSuccess(isSuccess)
                .categoryItem(mission.getCategoryItem() != null ? mission.getCategoryItem().name() : null)
                .build();
    }

    private TeamAchievementResponse buildTeamAchievement(MissionParticipant myParticipant) {
        Mission mission = myParticipant.getMission();
        Long missionId = mission.getMissionId();

        LocalDate startDate = mission.getRecruitEndDate().plusDays(1);
        LocalDate endDate = mission.getMissionDuration().endDate(startDate);

        List<MissionParticipant> activeParticipants = mission.getParticipants().stream()
                .filter(p -> p.getStatus() != MissionParticipantStatus.PENDING
                        && p.getStatus() != MissionParticipantStatus.REJECTED)
                .collect(Collectors.toList());

        int totalParticipants = activeParticipants.size();
        int requiredCount = (int) Math.ceil(totalParticipants / 2.0);

        List<Object[]> completedCounts = taskRepository.countCompletedByDate(missionId, startDate, endDate);
        Map<LocalDate, Integer> completedMap = completedCounts.stream()
                .collect(Collectors.toMap(row -> (LocalDate) row[0], row -> ((Long) row[1]).intValue()));

        // 캘린더 전체 날짜가 아니라, 반복요일이 실제로 반영된 "예정일"만 분모로 사용
        List<LocalDate> scheduledDates = missionParticipantService.resolveScheduledDates(mission, startDate, endDate);
        int totalScheduledDays = scheduledDates.size();

        long partyAchievedDays = scheduledDates.stream()
                .filter(date -> completedMap.getOrDefault(date, 0) >= requiredCount)
                .count();

        double partyAchievementRate = totalScheduledDays == 0 ? 0 :
                Math.round((double) partyAchievedDays / totalScheduledDays * 1000) / 10.0;

        // ✅ requiredDays 역산 추가
        int requiredDays = MissionPuzzleCalculator.getRequiredDays(totalScheduledDays);
        int puzzlePieces = MissionPuzzleCalculator.calculatePieces((int) partyAchievedDays, totalScheduledDays);
        boolean isTeamSuccess = MissionPuzzleCalculator.isSuccess((int) partyAchievedDays, totalScheduledDays);

        List<TeamAchievementResponse.MemberResult> memberResults = activeParticipants.stream()
                .map(TeamAchievementResponse.MemberResult::from)
                .collect(Collectors.toList());

        return TeamAchievementResponse.builder()
                .missionId(missionId)
                .missionTitle(mission.getMissionTitle())
                .missionStatus(mission.getMissionStatus().name())
                .progressStartDate(startDate)
                .progressEndDate(endDate)
                .partyAchievementRate(partyAchievementRate)
                .memberResults(memberResults)
                .achievedDays((int) partyAchievedDays)
                .requiredDays(requiredDays)
                .puzzlePieces(puzzlePieces)
                .isSuccess(isTeamSuccess)
                .build();
    }

    // ✅ calculateEndDate() 삭제 — 더 이상 어디서도 호출하지 않는 죽은 코드였음
    //    (endDate는 이미 mission.getMissionDuration().endDate(startDate)로 구하고 있음)
}