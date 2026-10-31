// service/MissionWidgetService.java
package com.naleul.naleul.domain.mission.service;

import com.naleul.naleul.domain.mission.dto.response.MissionWidgetResponse;
import com.naleul.naleul.domain.mission.entity.Mission;
import com.naleul.naleul.domain.mission.repository.MissionRepository;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionWidgetService {

    private final MissionRepository missionRepository;
    private final TaskRepository taskRepository; // ✅ MissionTaskCompleteRepository 불필요해짐

    public MissionWidgetResponse getWidgetStatus(Long missionId, Long userId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new CustomException(ErrorCode.MISSION_NOT_FOUND));

        // ✅ 이미 존재하는 메서드 재사용 (MissionAchievementService와 동일)
        List<Task> allTasks = taskRepository.findAllMissionTasks(missionId, userId);

        int totalTaskCount = allTasks.size();
        int completedTaskCount = (int) allTasks.stream()
                .filter(t -> t.getTaskStatus() == TaskStatus.COMPLETED)
                .count();
        double achievementRate = totalTaskCount == 0 ? 0 :
                Math.round((double) completedTaskCount / totalTaskCount * 1000) / 10.0;

        // ✅ routineDate 기준으로 이번 주(월~일) 데이터만 뽑기
        Map<LocalDate, TaskStatus> statusByDate = allTasks.stream()
                .collect(Collectors.toMap(Task::getRoutineDate, Task::getTaskStatus, (a, b) -> a));

        LocalDate today = LocalDate.now();
        LocalDate weekMonday = today.with(DayOfWeek.MONDAY);
        LocalDate weekSunday = today.with(DayOfWeek.SUNDAY);

        List<MissionWidgetResponse.WidgetDayRecord> weeklyRecords = weekMonday.datesUntil(weekSunday.plusDays(1))
                .map(date -> MissionWidgetResponse.WidgetDayRecord.builder()
                        .dayOfWeek(date.getDayOfWeek().name().substring(0, 3))
                        .date(date)
                        .completed(statusByDate.get(date) == TaskStatus.COMPLETED)
                        .build())
                .toList();

        // ✅ progressEndDate는 필드가 아니라 계산값 (recruitEndDate + missionDuration)
        LocalDate progressStart = mission.getRecruitEndDate().plusDays(1);
        LocalDate progressEnd = mission.getMissionDuration().endDate(progressStart);
        long daysLeft = Math.max(0, ChronoUnit.DAYS.between(today, progressEnd));

        return MissionWidgetResponse.builder()
                .missionId(mission.getMissionId())
                .missionTitle(mission.getMissionTitle())
                .categoryItem(mission.getCategoryItem())
                .missionStatus(mission.getMissionStatus())
                .achievementRate(achievementRate)
                .daysLeft(daysLeft)
                .weeklyRecords(weeklyRecords)
                .build();
    }
}