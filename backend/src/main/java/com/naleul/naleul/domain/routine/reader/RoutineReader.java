package com.naleul.naleul.domain.routine.reader;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.routine.dto.response.RoutineHeatmapResponse;
import com.naleul.naleul.domain.routine.dto.response.RoutineResponse;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.routine.service.RoutineFinder;
import com.naleul.naleul.domain.routine.util.RoutineTimeStatsCalculator;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoutineReader {

    private final RoutineRepository routineRepository;
    private final TaskRepository taskRepository;
    private final UserFinder userFinder;
    private final RoutineFinder routineFinder;
    private final CategoryFinder categoryFinder;

    public RoutineResponse getRoutine(Long userId, Long routineId) {
        User user = userFinder.findUser(userId);
        Routine routine = routineFinder.findByIdAndUser(routineId, user); // 소유권 확인은 Finder가 담당
        return RoutineResponse.from(routine);
    }

    public List<RoutineResponse> getRoutines(Long userId) {
        User user = userFinder.findUser(userId);
        return routineRepository.findAllByUser(user)
                .stream()
                .map(RoutineResponse::from)
                .toList();
    }

    public List<RoutineResponse> getAllRoutinesForDisplay(Long userId) {
        User user = userFinder.findUser(userId);
        return routineRepository.findAllByUserWithDetails(user)
                .stream()
                .map(RoutineResponse::from)
                .toList();
    }

    /**
     * 목표 상세 "루틴 실천" 히트맵 — 내 목표인지 먼저 확인한다.
     * (예전엔 userId 를 받고도 확인하지 않아서, 다른 사람의 목표 id 를 넣으면 그 루틴 기록이 보였다)
     */
    public List<RoutineHeatmapResponse> getHeatmap(Long userId, Long goalCategoryId) {
        categoryFinder.findGoalCategory(goalCategoryId, userFinder.findUser(userId));   // 내 목표가 아니면 404
        return getHeatmap(goalCategoryId);
    }

    public List<RoutineHeatmapResponse> getHeatmap(Long goalCategoryId) {
        List<Routine> routines = routineRepository.findAllByGoalCategoryId(goalCategoryId);
        if (routines.isEmpty()) return List.of();

        List<Long> routineIds = routines.stream().map(Routine::getRoutineId).toList();
        Map<Long, List<LocalDate>> completedDatesByRoutineId =
                taskRepository.findCompletedDatesGroupedByRoutineId(routineIds);
        Map<Long, List<RoutineTimeStatsCalculator.Practice>> practices = RoutineTimeStatsCalculator.practicesByRoutine(
                taskRepository.findCompletedPracticeTimesByRoutineIds(routineIds));

        return routines.stream()
                .map(routine -> RoutineHeatmapResponse.of(
                        routine,
                        completedDatesByRoutineId.getOrDefault(routine.getRoutineId(), List.of()),
                        RoutineTimeStatsCalculator.calculate(practices.get(routine.getRoutineId()))
                ))
                .toList();
    }
}