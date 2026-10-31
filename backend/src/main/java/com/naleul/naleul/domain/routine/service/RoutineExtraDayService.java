package com.naleul.naleul.domain.routine.service;

import com.naleul.naleul.domain.actualTask.dto.request.TaskUpdateActualRequest;
import com.naleul.naleul.domain.actualTask.service.TaskActualService;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.routine.dto.request.RoutineExtraDayRequest;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.enums.RoutineStatus;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * 루틴을 반복 요일이 아닌 날에 한 번 더 — "평일 루틴인데 주말에도 했어요".
 *
 * 그날 루틴 Task 를 하나 만들어요 (루틴 Task 라서 루틴 실천 히트맵 · 나의 패턴에 같이 세져요).
 *  - done = true : 그 시간에 한 것으로 바로 완료 (실제로 한 시간도 남겨요). 오늘 · 지난날만
 *  - done = false: 그날 할 일로 추가 (오늘 ~ 60일 뒤)
 *
 * ⚠️ 루틴을 나중에 수정하면 오늘 이후의 "완료 안 한" 루틴 Task 는 새 요일로 다시 만들어져요.
 *    그래서 앞날에 추가해 둔 날은 루틴 수정 때 빠질 수 있어요 (완료한 날은 그대로 남아요).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RoutineExtraDayService {

    static final int MAX_DAYS_AHEAD = 60;

    private final UserFinder userFinder;
    private final RoutineFinder routineFinder;
    private final TaskRepository taskRepository;
    private final TaskActualService taskActualService;
    private final PlannerClock clock;

    /** @return { "taskId": 123 } */
    public Map<String, Long> add(Long userId, Long routineId, RoutineExtraDayRequest req) {
        User user = userFinder.findUser(userId);
        Routine routine = routineFinder.findByIdAndUser(routineId, user);
        if (routine.getRoutineStatus() == RoutineStatus.DELETED || routine.getRoutineStatus() == RoutineStatus.STOPPED) {
            throw new CustomException(ErrorCode.ROUTINE_NOT_ACTIVE);
        }
        LocalDate today = clock.today();
        LocalDate date = req.date();
        if ((req.done() && date.isAfter(today)) || date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new CustomException(ErrorCode.ROUTINE_EXTRA_DAY_INVALID);
        }
        if (taskRepository.existsByRoutineAndRoutineDate(routine, date)) {
            throw new CustomException(ErrorCode.ROUTINE_EXTRA_DAY_EXISTS);
        }

        Task task = Task.fromRoutine(routine, date);
        LocalTime start = req.startTime() != null ? req.startTime() : routine.startTimeOn(date.getDayOfWeek());
        LocalTime end = req.endTime() != null ? req.endTime()
                : req.startTime() != null && routine.getDurationMinutes() != null
                ? req.startTime().plusMinutes(routine.getDurationMinutes())
                : routine.endTimeOn(date.getDayOfWeek());
        if (req.startTime() != null && end == null) end = req.startTime().plusMinutes(30);
        if (start != null && end != null) {
            LocalDateTime s = date.atTime(start);
            LocalDateTime e = end.isAfter(start) ? date.atTime(end) : date.plusDays(1).atTime(end);
            if (java.time.Duration.between(s, e).toMinutes() > 12 * 60) {
                throw new CustomException(ErrorCode.ROUTINE_INVALID_TIME);
            }
            task.updateSchedule(s, e);
        }
        taskRepository.saveAndFlush(task);

        if (req.done()) {
            if (task.getPlannedStartAt() != null && task.getPlannedEndAt() != null) {
                taskActualService.recordActual(userId, task.getTaskId(),
                        new TaskUpdateActualRequest(task.getPlannedStartAt(), task.getPlannedEndAt()));
            } else {
                task.complete();
            }
        }
        return Map.of("taskId", task.getTaskId());
    }
}
