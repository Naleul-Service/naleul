package com.naleul.naleul.domain.mission.service;

import com.naleul.naleul.domain.mission.dto.response.PartyAchievementResponse;
import com.naleul.naleul.domain.mission.entity.Mission;
import com.naleul.naleul.domain.mission.entity.MissionParticipant;
import com.naleul.naleul.domain.mission.enums.MissionParticipantStatus;
import com.naleul.naleul.domain.mission.enums.RepeatDayType;
import com.naleul.naleul.domain.mission.repository.MissionRepository;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartyAchievementService {

    private final MissionRepository missionRepository;
    private final TaskRepository taskRepository;

    public PartyAchievementResponse getPartyAchievement(Long missionId) {
        Mission mission = missionRepository.findDetailById(missionId)
                .orElseThrow(() -> new EntityNotFoundException("미션을 찾을 수 없습니다."));

        LocalDate startDate = mission.getRecruitEndDate().plusDays(1);
        LocalDate endDate = calculateEndDate(mission);
        LocalDate today = LocalDate.now();

        int totalParticipants = (int) mission.getParticipants().stream()
                .filter(this::isDisplayableParticipant)
                .count();

        int requiredCount = (int) Math.ceil(totalParticipants / 2.0);

        List<Object[]> completedCounts = taskRepository.countCompletedByDate(
                missionId, startDate, endDate);

        Map<LocalDate, Integer> completedMap = completedCounts.stream()
                .collect(Collectors.toMap(
                        row -> (LocalDate) row[0],
                        row -> ((Long) row[1]).intValue()
                ));

        List<PartyAchievementResponse.PartyGrassCell> grassCells =
                startDate.datesUntil(endDate.plusDays(1))
                        .filter(date -> isTargetDay(mission, date))
                        .map(date -> {
                            int completedCount = completedMap.getOrDefault(date, 0);
                            boolean partyAchieved = completedCount >= requiredCount;
                            return PartyAchievementResponse.PartyGrassCell.builder()
                                    .date(date)
                                    .completedCount(completedCount)
                                    .partyAchieved(partyAchieved)
                                    .build();
                        })
                        .collect(Collectors.toList());

        // ✅ totalDays를 achievementRate 계산 "전에" 구하도록 위로 이동
        long totalDays = startDate.datesUntil(endDate.plusDays(1))
                .filter(date -> isTargetDay(mission, date))
                .count();

        long partyAchievedDays = grassCells.stream()
                .filter(PartyAchievementResponse.PartyGrassCell::isPartyAchieved)
                .count();

        // ✅ 핵심 수정 — 분모를 passedDays(오늘까지 지난 날) 대신 totalDays(전체 예정일)로 고정
        double achievementRate = totalDays == 0 ? 0 :
                Math.round((double) partyAchievedDays / totalDays * 1000) / 10.0;

        // ✅ progressRate 계산용으로만 쓰는 값 — 이름을 elapsedDays로 통일 (passedDays 삭제)
        long elapsedDays = today.isBefore(startDate) ? 0 :
                today.isAfter(endDate) ? totalDays :
                        startDate.datesUntil(today.plusDays(1))
                        .filter(date -> isTargetDay(mission, date))
                        .count();

        double progressRate = totalDays == 0 ? 0 :
                Math.round((double) elapsedDays / totalDays * 1000) / 10.0;

        return PartyAchievementResponse.builder()
                .missionId(missionId)
                .totalParticipants(totalParticipants)
                .requiredCount(requiredCount)
                .achievementRate(achievementRate)
                .progressRate(progressRate)
                .startDate(startDate)
                .endDate(endDate)
                .grassCells(grassCells)
                .build();
    }

    // ✅ 추가 — 다른 Service들과 동일한 필터 (MissionDailyStatusService, MissionProgressService와 동일 패턴)
    private boolean isDisplayableParticipant(MissionParticipant p) {
        MissionParticipantStatus status = p.getStatus();
        return status == MissionParticipantStatus.ACTIVE
                || status == MissionParticipantStatus.COMPLETED
                || status == MissionParticipantStatus.FAILED;
    }

    private boolean isTargetDay(Mission mission, LocalDate date) {
        DayOfWeek weekday = date.getDayOfWeek();

        return switch (mission.getRepeatDayType()) {
            case EVERYDAY -> true;
            case WEEKDAYS -> weekday != DayOfWeek.SATURDAY && weekday != DayOfWeek.SUNDAY;
            case CUSTOM -> {
                Set<DayOfWeek> targetDays = mission.getRepeatDays().stream()
                        .map(MissionRepeatDay -> MissionRepeatDay.getDayOfWeek())
                        .collect(Collectors.toSet());
                yield targetDays.contains(weekday);
            }
        };
    }

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