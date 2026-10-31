package com.naleul.naleul.domain.pattern.service;

import com.naleul.naleul.domain.pattern.entity.BrainDumpLog;
import com.naleul.naleul.domain.pattern.entity.TaskScheduleLog;
import com.naleul.naleul.domain.pattern.enums.TaskScheduleAction;
import com.naleul.naleul.domain.pattern.repository.BrainDumpLogRepository;
import com.naleul.naleul.domain.pattern.repository.TaskScheduleLogRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.task.entity.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 나의 패턴용 일정 변경 기록을 남긴다 (명세 4 · 6-2).
 *
 * 호출하는 쪽(배치·드래그·수정·이월·Brain dump)의 트랜잭션 안에서 함께 저장된다.
 * 기록은 부가 기능이므로 기록 실패가 본 작업(드래그, 22시 생성 등)을 실패시키면 안 된다 → 예외를 잡고 로그만 남긴다.
 *
 * 테스트에서 저장소 없이 쓰려면 {@link #noop()} 을 사용한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskScheduleLogWriter {

    private final TaskScheduleLogRepository taskScheduleLogRepository;
    private final BrainDumpLogRepository brainDumpLogRepository;
    private final PlannerClock clock;

    /** 아무것도 저장하지 않는 기록기 (단위 테스트용) */
    public static TaskScheduleLogWriter noop() {
        return new TaskScheduleLogWriter(null, null, null);
    }

    /**
     * 엔진이 Task 시간을 정함.
     * @param previousStart 엔진이 다시 배치하느라 비우기 전 시각 (처음 배치면 null). 같은 자리에 다시 놓였으면 기록하지 않는다.
     */
    public void enginePlaced(Long userId, Task task, LocalDateTime previousStart) {
        if (previousStart != null && previousStart.equals(task.getPlannedStartAt())) return;
        write(userId, task, TaskScheduleAction.ENGINE_PLACE,
                previousStart != null ? previousStart.toLocalDate() : null, previousStart);
    }

    /**
     * 사용자가 드래그·수정으로 시간을 바꿈. 시각이 실제로 바뀐 경우만 기록한다.
     * @param fromDate  바꾸기 전 Task 날짜 (시간이 없던 Task 면 routine_date)
     * @param fromStart 바꾸기 전 시작 시각 (없으면 null)
     */
    public void userMoved(Long userId, Task task, LocalDate fromDate, LocalDateTime fromStart) {
        if (Objects.equals(fromStart, task.getPlannedStartAt())) return;
        write(userId, task, TaskScheduleAction.USER_MOVE, fromDate, fromStart);
    }

    /** 사용자가 "시간 미정"으로 되돌림 */
    public void userUnscheduled(Long userId, Task task, LocalDate fromDate, LocalDateTime fromStart) {
        if (fromStart == null) return; // 원래 시간이 없었으면 바뀐 게 없음
        write(userId, task, TaskScheduleAction.USER_UNSCHEDULE, fromDate, fromStart);
    }

    /**
     * 22시 이월. carryOverTo 를 호출하기 "전에" 불러야 원래 날짜·시각이 남는다.
     * from = 원래 날짜·시각 (= 그날의 놓침 1건), to = 이월될 날짜 (시각 없음)
     */
    public void carriedOver(Long userId, Task task, LocalDate toDate) {
        if (!enabled()) return;
        try {
            taskScheduleLogRepository.save(TaskScheduleLog.of(userId, task.getTaskId(), routineIdOf(task),
                    TaskScheduleAction.CARRY_OVER, dateOf(task), task.getPlannedStartAt(),
                    toDate, null, clock.now()));
        } catch (RuntimeException e) {
            log.warn("[ScheduleLog] 이월 기록 실패 userId={} taskId={}", userId, task.getTaskId(), e);
        }
    }

    /** Brain dump 확정 1회 */
    public void brainDumpConfirmed(Long userId, int itemCount, int tempGoalCount) {
        if (!enabled()) return;
        try {
            brainDumpLogRepository.save(BrainDumpLog.of(userId, itemCount, tempGoalCount, clock.now()));
        } catch (RuntimeException e) {
            log.warn("[ScheduleLog] Brain dump 기록 실패 userId={}", userId, e);
        }
    }

    /** Task 의 날짜: 시간이 있으면 시작 시각의 날짜, 없으면 routine_date */
    public static LocalDate dateOf(Task task) {
        return task.getPlannedStartAt() != null ? task.getPlannedStartAt().toLocalDate() : task.getRoutineDate();
    }

    // ── 내부 ───────────────────────────────────────────────

    /** to = Task 의 현재(바뀐 뒤) 날짜·시각 */
    private void write(Long userId, Task task, TaskScheduleAction action, LocalDate fromDate, LocalDateTime fromStart) {
        if (!enabled()) return;
        if (task.getTaskId() == null) return; // 아직 저장 전 Task 는 기록할 수 없음
        try {
            taskScheduleLogRepository.save(TaskScheduleLog.of(userId, task.getTaskId(), routineIdOf(task), action,
                    fromDate, fromStart, dateOf(task), task.getPlannedStartAt(), clock.now()));
        } catch (RuntimeException e) {
            log.warn("[ScheduleLog] 기록 실패 userId={} taskId={} action={}", userId, task.getTaskId(), action, e);
        }
    }

    private boolean enabled() {
        return taskScheduleLogRepository != null && brainDumpLogRepository != null && clock != null;
    }

    /** 지연 로딩 프록시라도 id 조회는 DB 를 다시 읽지 않는다 */
    private static Long routineIdOf(Task task) {
        return task.getRoutine() != null ? task.getRoutine().getRoutineId() : null;
    }
}
