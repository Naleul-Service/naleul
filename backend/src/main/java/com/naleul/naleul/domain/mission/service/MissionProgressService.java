package com.naleul.naleul.domain.mission.service;

import com.naleul.naleul.domain.mission.dto.response.MissionGrassMapResponse;
import com.naleul.naleul.domain.mission.dto.response.MissionMonthlyPhotoResponse;
import com.naleul.naleul.domain.mission.entity.Mission;
import com.naleul.naleul.domain.mission.entity.MissionTaskComplete;
import com.naleul.naleul.domain.mission.repository.MissionRepository;
import com.naleul.naleul.domain.mission.repository.MissionTaskCompleteRepository;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.naleul.naleul.domain.mission.dto.response.MissionDailyDetailResponse;
import com.naleul.naleul.domain.mission.entity.MissionParticipant;
import com.naleul.naleul.domain.mission.enums.MissionParticipantStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionProgressService {

    private final MissionRepository missionRepository;
    private final TaskRepository taskRepository;
    private final MissionTaskCompleteRepository missionTaskCompleteRepository;

    // 잔디맵 조회 (미션 전체 기간) — 변경 없음
    public MissionGrassMapResponse getGrassMap(Long userId, Long missionId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new EntityNotFoundException("미션을 찾을 수 없습니다."));

        LocalDate startDate = mission.getRecruitEndDate().plusDays(1);
        LocalDate endDate = calculateEndDate(mission);

        List<Task> tasks = taskRepository.findMissionTasks(
                missionId, userId, startDate, endDate);

        Map<LocalDate, Boolean> completedMap = tasks.stream()
                .collect(Collectors.toMap(
                        Task::getRoutineDate,
                        t -> t.getTaskStatus() == TaskStatus.COMPLETED
                ));

        List<MissionGrassMapResponse.GrassCell> cells = startDate.datesUntil(endDate.plusDays(1))
                .map(date -> MissionGrassMapResponse.GrassCell.builder()
                        .date(date)
                        .completed(completedMap.getOrDefault(date, false))
                        .build())
                .collect(Collectors.toList());

        return MissionGrassMapResponse.builder()
                .missionId(missionId)
                .userId(userId)
                .startDate(startDate)
                .endDate(endDate)
                .grassCells(cells)
                .build();
    }

    // 월별 인증사진 조회 — 변경 없음
    public MissionMonthlyPhotoResponse getMonthlyPhotos(Long userId, Long missionId,
                                                        int year, int month) {
        List<MissionTaskComplete> completes = missionTaskCompleteRepository
                .findMonthlyCompletes(missionId, userId, year, month);

        List<MissionMonthlyPhotoResponse.PhotoCell> photos = completes.stream()
                .map(MissionMonthlyPhotoResponse.PhotoCell::from)
                .collect(Collectors.toList());

        return MissionMonthlyPhotoResponse.builder()
                .year(year)
                .month(month)
                .photos(photos)
                .build();
    }

    // 특정 날짜의 미션 진행 상세 (달성률 + 참여자별 인증 여부/사진)
    public MissionDailyDetailResponse getDailyDetail(Long missionId, LocalDate date) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new EntityNotFoundException("미션을 찾을 수 없습니다."));

        // ✅ 변경 — ACTIVE뿐 아니라 완료 후 확정된 참여자(COMPLETED/FAILED)도 포함
        //    (PENDING, REJECTED는 여전히 제외)
        List<MissionParticipant> targetParticipants = mission.getParticipants().stream()
                .filter(this::isDisplayableParticipant)
                .collect(Collectors.toList());

        List<MissionTaskComplete> completes = missionTaskCompleteRepository.findDailyCompletes(missionId, date);
        Map<Long, MissionTaskComplete> completeByUserId = completes.stream()
                .collect(Collectors.toMap(c -> c.getUser().getUserId(), c -> c));

        List<MissionDailyDetailResponse.ParticipantDaily> participantDailies = targetParticipants.stream()
                .map(p -> {
                    MissionTaskComplete complete = completeByUserId.get(p.getUser().getUserId());
                    boolean completed = complete != null;
                    return MissionDailyDetailResponse.ParticipantDaily.builder()
                            .userId(p.getUser().getUserId())
                            .userName(p.getUser().getUserName())
                            .completed(completed)
                            .imageUrl(completed ? complete.getImageUrl() : null)
                            .comment(completed ? complete.getComment() : null)
                            .build();
                })
                .collect(Collectors.toList());

        int total = targetParticipants.size();
        long completedCount = participantDailies.stream()
                .filter(MissionDailyDetailResponse.ParticipantDaily::isCompleted)
                .count();
        double achievementRate = total == 0 ? 0.0 : (completedCount * 100.0 / total);

        return MissionDailyDetailResponse.builder()
                .date(date)
                .achievementRate(achievementRate)
                .participants(participantDailies)
                .build();
    }

    // ✅ 추가 — MissionDailyStatusService와 동일한 필터 (재사용 위해 나중에 공통 유틸로 뽑아도 좋아요)
    private boolean isDisplayableParticipant(MissionParticipant p) {
        MissionParticipantStatus status = p.getStatus();
        return status == MissionParticipantStatus.ACTIVE
                || status == MissionParticipantStatus.COMPLETED
                || status == MissionParticipantStatus.FAILED;
    }

    // ===== 날짜 계산 =====

    private LocalDate calculateEndDate(Mission mission) {
        LocalDate start = mission.getRecruitEndDate().plusDays(1);
        return switch (mission.getMissionDuration()) {
            case ONE_WEEK     -> start.plusWeeks(1).minusDays(1);
            case TWO_WEEKS    -> start.plusWeeks(2).minusDays(1);
            case THREE_WEEKS  -> start.plusWeeks(3).minusDays(1);
            case ONE_MONTH    -> start.plusMonths(1).minusDays(1);
            case TWO_MONTHS   -> start.plusMonths(2).minusDays(1);
            case THREE_MONTHS -> start.plusMonths(3).minusDays(1);
        };
    }
}