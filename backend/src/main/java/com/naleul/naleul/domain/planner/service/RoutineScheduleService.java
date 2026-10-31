package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.planner.dto.request.RoutineScheduleRequest;
import com.naleul.naleul.domain.planner.dto.request.TaskScheduleRequest;
import com.naleul.naleul.domain.planner.dto.response.RoutineScheduleResponse;
import com.naleul.naleul.domain.planner.dto.response.TaskScheduleResponse;
import com.naleul.naleul.domain.planner.enums.ChangeScope;
import com.naleul.naleul.domain.planner.repository.TimetableQueryRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.service.TaskFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 캘린더에서 루틴 블록 시간을 바꿀 때 적용 범위 고르기 (이날만 / 이번 주 / 앞으로).
 *
 *  - DAY       : 그 Task 하나만 (기존 PATCH /schedule 과 같음)
 *  - WEEK      : 그날 + 그 주 일요일까지 같은 루틴의 남은 Task 를 같은 시각으로
 *  - FOLLOWING : 그날 + 루틴 종료일까지 남은 Task 를 같은 시각으로 + 루틴 기본 시간도 변경
 *
 * 기존 루틴 수정(RoutineService.update)은 앞으로의 Task 를 지우고 다시 만들어서,
 * 날마다 따로 옮겨 둔 시간·자동 배치 결과가 사라진다. 여기서는 남은 Task 를 하나씩 옮긴다.
 * 오늘보다 이전 날짜는 건드리지 않는다 (놓친 기록 보존).
 * 잠긴 일정과 겹치는 날은 건너뛰고 skippedDates 로 알려준다 (드래그한 그날이 겹치면 409).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RoutineScheduleService {

    private final TaskScheduleService taskScheduleService;
    private final TaskFinder taskFinder;
    private final TimetableQueryRepository timetableQueryRepository;
    private final PlannerClock clock;

    public RoutineScheduleResponse change(Long userId, Long taskId, RoutineScheduleRequest request) {
        ChangeScope scope = request.scope() != null ? request.scope() : ChangeScope.DAY;
        LocalDateTime start = request.plannedStartAt();
        LocalDateTime end = request.plannedEndAt();
        Task task = taskFinder.findTaskWithDetails(taskId, userId);

        if (scope == ChangeScope.WEEKDAY) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE); // 요일별 범위는 고정 시간 전용
        }
        if (scope != ChangeScope.DAY) {
            if (!task.isRoutineTask() || task.getRoutineDate() == null) {
                throw new CustomException(ErrorCode.SCOPE_ROUTINE_ONLY);
            }
            if (!start.toLocalDate().equals(task.getRoutineDate())) {
                throw new CustomException(ErrorCode.SCOPE_SAME_DAY_ONLY);
            }
            // 앞으로: 자정을 넘겨도 돼요 (23:00 ~ 00:00 · 23:30 ~ 01:00) — 루틴엔 "끝 < 시작"으로 저장되고
            // Task 를 만들 때 다음 날 그 시각으로 끝나요 (Task.fromRoutine). 자정을 넘기면 12시간까지 (루틴 검증과 같음)
            boolean crosses = !end.toLocalDate().equals(start.toLocalDate());
            if (scope == ChangeScope.FOLLOWING && (!end.isAfter(start)
                    || (crosses && Duration.between(start, end).toMinutes() > 12 * 60))) {
                throw new CustomException(ErrorCode.SCOPE_NO_MIDNIGHT);
            }
        }

        // 1) 드래그한 그날 (겹치면 409 → 아무것도 바뀌지 않음)
        TaskScheduleResponse main = taskScheduleService.reschedule(userId, taskId, new TaskScheduleRequest(start, end));
        if (scope == ChangeScope.DAY) {
            return new RoutineScheduleResponse(scope, main, List.of(), List.of());
        }

        // 2) 같은 루틴의 남은 날들 (오늘 이전은 제외)
        Routine routine = task.getRoutine();
        LocalDate day = task.getRoutineDate();
        LocalDate today = clock.today();
        LocalDate from = max(day.plusDays(1), today);
        LocalDate to = scope == ChangeScope.WEEK
                ? day.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                : routine.getRepeatEndDate();

        LocalTime newStart = start.toLocalTime();
        long minutes = Duration.between(start, end).toMinutes();
        List<LocalDate> changed = new ArrayList<>();
        List<LocalDate> skipped = new ArrayList<>();

        if (to != null && !from.isAfter(to)) {
            for (Task other : timetableQueryRepository.findRoutineTodoBetween(userId, routine.getRoutineId(), from, to)) {
                LocalDateTime s = other.getRoutineDate().atTime(newStart);
                LocalDateTime e = s.plusMinutes(minutes);
                if (Objects.equals(other.getPlannedStartAt(), s) && Objects.equals(other.getPlannedEndAt(), e)) continue;
                if (taskScheduleService.tryReschedule(userId, other.getTaskId(), s, e) != null) {
                    changed.add(other.getRoutineDate());
                } else {
                    skipped.add(other.getRoutineDate());
                }
            }
        }

        // 3) 앞으로: 루틴 기본 시간도 바꿔 둔다 (나중에 기간을 늘려 새로 만들어지는 Task 도 같은 시각)
        if (scope == ChangeScope.FOLLOWING) {
            routine.update(null, null, null, null, null, newStart, end.toLocalTime(), null);
        }
        return new RoutineScheduleResponse(scope, main, changed, skipped);
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
