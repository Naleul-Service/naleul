package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.lifePattern.service.FixedBlockResolver;
import com.naleul.naleul.domain.lifePattern.support.FixedBlock;
import com.naleul.naleul.domain.pattern.enums.TaskScheduleAction;
import com.naleul.naleul.domain.pattern.service.TaskScheduleLogWriter;
import com.naleul.naleul.domain.planner.dto.request.TaskBatchScheduleRequest;
import com.naleul.naleul.domain.planner.dto.request.TaskMoveDateRequest;
import com.naleul.naleul.domain.planner.dto.request.TaskScheduleRequest;
import com.naleul.naleul.domain.planner.dto.response.TaskBatchScheduleResponse;
import com.naleul.naleul.domain.planner.dto.response.TaskQuotaResponse;
import com.naleul.naleul.domain.planner.dto.response.TaskScheduleResponse;
import com.naleul.naleul.domain.planner.dto.response.TimeBlockTaskResponse;
import com.naleul.naleul.domain.planner.repository.PlacementQueryRepository;
import com.naleul.naleul.domain.planner.support.Concurrency;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskPriority;
import com.naleul.naleul.domain.task.enums.PlacedBy;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.policy.TaskQuotaValidator;
import com.naleul.naleul.domain.task.service.TaskFinder;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import com.naleul.naleul.global.policy.PlanLimits;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 캘린더에서 Task 블록을 직접 조작 (명세 3 · 3-4, 5-3).
 *
 * 드래그 규칙
 *  - 옮긴 Task 는 잠긴다 (자동 배치가 다시 옮기지 않음)
 *  - 고정 시간 위에 놓아도 저장 → Task 가 그 시간을 덮는다 (coveredFixedBlocks 로 알려줌)
 *  - 잠긴 Task·완료한 Task·미션 Task 와는 한 시각에 2개까지 겹쳐 둘 수 있다. 3개째가 되면 409 TIMEBLOCK_CONFLICT
 *    (먼저 새 자리에 놓고 원래 있던 Task 를 나중에 옮기는 식의 정리를 할 수 있게)
 *  - 완료한 Task 는 "실제로 한 시간"이 자리를 차지한다 (계획 자리는 비어 있음)
 *  - 자동 배치 블록과 겹치면 옮긴 Task 가 이기고, 밀려난 블록은 그날 빈 시간에 다시 배치 (없으면 마감일 안의 다음 날)
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TaskScheduleService {

    private static final long MAX_BLOCK_MINUTES = 24 * 60;

    private final TaskFinder taskFinder;
    private final PlacementQueryRepository placementQueryRepository;
    private final DayPlacementService dayPlacementService;
    private final FixedBlockResolver fixedBlockResolver;
    private final PlannerSettingService plannerSettingService;
    private final TaskQuotaValidator taskQuotaValidator;
    private final UserFinder userFinder;
    private final PlannerClock clock;
    /** 나의 패턴: 사용자 이동 기록 (자주 옮기는 블록, AI 배치 수정률, 명세 4 · 6-2) */
    private final TaskScheduleLogWriter scheduleLogWriter;
    /** 완료한 Task 의 실제 시각. 단위 테스트처럼 없으면 계획 시각으로 판정한다 */
    @Autowired(required = false)
    private ActualRangeReader actualRangeReader;
    /** 이월 되돌리기: 원래 시각을 이월 기록에서 찾는다 (단위 테스트처럼 없으면 그날 시간 미정으로) */
    @Autowired(required = false)
    private com.naleul.naleul.domain.pattern.repository.TaskScheduleLogRepository scheduleLogRepository;

    public TaskScheduleResponse reschedule(Long userId, Long taskId, TaskScheduleRequest request) {
        return reschedule(userId, taskId, request, true);
    }

    /**
     * @param recordMove false 면 "사용자가 옮김" 기록을 남기지 않는다.
     *                   방금 만든 Task 에 처음 시간을 정할 때 쓴다 (옮긴 게 아니라서 나의 패턴 이동 통계에 넣지 않음).
     */
    public TaskScheduleResponse reschedule(Long userId, Long taskId, TaskScheduleRequest request, boolean recordMove) {
        LocalDateTime start = request.plannedStartAt();
        LocalDateTime end = request.plannedEndAt();
        validateRange(start, end);
        plannerSettingService.getOrCreateForUpdate(userId); // 같은 사용자의 배치와 동시에 돌지 않게

        Task task = findLive(userId, taskId);
        List<Task> pushed = pushableOrNull(userId, taskId, start, end);
        if (pushed == null) {
            throw new CustomException(ErrorCode.TIMEBLOCK_CONFLICT);
        }
        return apply(userId, task, start, end, pushed, recordMove);
    }

    /**
     * reschedule 과 같지만, 잠긴 일정과 겹치면 예외 대신 null 을 돌려준다 (아무것도 바꾸지 않음).
     * 루틴을 "이번 주 / 앞으로" 한꺼번에 옮길 때, 겹치는 날만 건너뛰려고 쓴다.
     * (예외를 던지면 바깥 트랜잭션 전체가 롤백 표시되므로 예외 없이 알려줘야 한다)
     */
    public TaskScheduleResponse tryReschedule(Long userId, Long taskId, LocalDateTime start, LocalDateTime end) {
        validateRange(start, end);
        Task task = findLive(userId, taskId);
        List<Task> pushed = pushableOrNull(userId, taskId, start, end);
        if (pushed == null) return null;
        return apply(userId, task, start, end, pushed, true);
    }

    private void validateRange(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start) || Duration.between(start, end).toMinutes() > MAX_BLOCK_MINUTES) {
            throw new CustomException(ErrorCode.TASK_INVALID_TIME_RANGE);
        }
    }

    private Task findLive(Long userId, Long taskId) {
        Task task = taskFinder.findTaskWithDetails(taskId, userId);
        if (task.getTaskStatus() == TaskStatus.DELETED) {
            throw new CustomException(ErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    /**
     * [start, end) 에 겹치는 다른 Task 분류.
     * @return 밀어낼 자동 배치 블록 목록.
     *         밀어낼 수 없는 Task(잠김·완료·미션)가 이미 한 시각에 2개 놓여 있는 곳과 겹치면 null
     */
    private List<Task> pushableOrNull(Long userId, Long taskId, LocalDateTime start, LocalDateTime end) {
        return pushableOrNull(userId, Set.of(taskId), start, end, List.of());
    }

    /**
     * @param movingIds   지금 같이 옮기는 Task 들 (원래 자리는 겹침 판정에서 뺀다)
     * @param movingRanges 같이 옮기는 다른 Task 들의 "새" 자리 (이것도 자리를 차지한다)
     */
    private List<Task> pushableOrNull(Long userId, Set<Long> movingIds, LocalDateTime start, LocalDateTime end,
                                      List<DateTimeRange> movingRanges) {
        DateTimeRange target = new DateTimeRange(start, end);
        List<Task> pushed = new ArrayList<>();
        List<DateTimeRange> blockers = new ArrayList<>(movingRanges);
        for (Map.Entry<Task, DateTimeRange> e : occupants(userId, start, end).entrySet()) {
            Task other = e.getKey();
            if (movingIds.contains(other.getTaskId())) continue;
            if (isMovableEngineBlock(other)) {
                pushed.add(other);
            } else {
                blockers.add(e.getValue());
            }
        }
        // 이미 2개가 같이 놓인 순간이 있으면 3개째는 막는다
        if (Concurrency.maxOverlap(blockers, target) >= Concurrency.MAX_TASKS_AT_ONCE) return null;
        return pushed;
    }

    /**
     * [start, end) 와 겹치는 다른 Task 와 그 Task 가 차지하는 구간 (건너뜀·삭제 제외).
     * 완료한 Task 는 실제로 한 시간, 나머지는 계획 시간 기준.
     */
    private Map<Task, DateTimeRange> occupants(Long userId, LocalDateTime start, LocalDateTime end) {
        DateTimeRange target = new DateTimeRange(start, end);
        Map<Long, Task> others = new LinkedHashMap<>();
        for (LocalDate d = start.toLocalDate(); !d.isAfter(end.minusNanos(1).toLocalDate()); d = d.plusDays(1)) {
            for (Task t : placementQueryRepository.findForPlacement(userId, d, start, end)) others.putIfAbsent(t.getTaskId(), t);
        }
        // 계획은 다른 시간이지만 실제로 이 시간에 완료한 Task 도 자리를 차지한다
        if (actualRangeReader != null) {
            for (Task t : actualRangeReader.completedWithActualIn(userId, start, end)) others.putIfAbsent(t.getTaskId(), t);
        }
        Map<Long, DateTimeRange> actuals = actualRangeReader != null
                ? actualRangeReader.actualRanges(others.values()) : Map.of();
        Map<Task, DateTimeRange> result = new LinkedHashMap<>();
        for (Task t : others.values()) {
            if (t.getTaskStatus() == TaskStatus.SKIPPED || t.getTaskStatus() == TaskStatus.DELETED) continue;
            DateTimeRange r = ActualRangeReader.occupied(t, actuals.get(t.getTaskId()), 30);
            if (r != null && r.overlaps(target)) result.put(t, r);
        }
        return result;
    }

    /** [start, end) 가 taskId 말고 아무 Task 와도 겹치지 않는지 (AI 배치 제안을 그대로 확정할 때) */
    public boolean isFree(Long userId, Long taskId, LocalDateTime start, LocalDateTime end) {
        validateRange(start, end);
        return occupants(userId, start, end).keySet().stream().allMatch(t -> t.getTaskId().equals(taskId));
    }

    /**
     * 여러 Task 를 한 번에 옮기기 (연속으로 쪼개 둔 Task 묶음 드래그).
     * 전부 먼저 검사하고(서로는 겹침에서 제외), 하나라도 자리가 없으면 아무것도 바꾸지 않고 409.
     */
    public TaskBatchScheduleResponse rescheduleBatch(Long userId, List<TaskBatchScheduleRequest.Item> items) {
        if (items == null || items.isEmpty()) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        plannerSettingService.getOrCreateForUpdate(userId);
        Set<Long> ids = new HashSet<>();
        for (TaskBatchScheduleRequest.Item it : items) {
            validateRange(it.plannedStartAt(), it.plannedEndAt());
            if (!ids.add(it.taskId())) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        // 같이 옮기는 Task 들의 새 자리 (묶음 안에서도 한 시각에 3개 이상 겹치지 않게)
        List<DateTimeRange> mine = items.stream()
                .map(it -> new DateTimeRange(it.plannedStartAt(), it.plannedEndAt())).toList();

        List<Task> tasks = new ArrayList<>();
        List<List<Task>> pushedPerItem = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            TaskBatchScheduleRequest.Item it = items.get(i);
            Task task = findLive(userId, it.taskId());
            if (task.getTaskStatus() == TaskStatus.COMPLETED || task.isMissionTask()) {
                throw new CustomException(ErrorCode.TIMEBLOCK_CONFLICT);
            }
            List<DateTimeRange> others = new ArrayList<>(mine);
            others.remove(i); // 자기 자신의 새 자리는 빼고, 같이 옮기는 나머지의 새 자리는 자리 차지로 본다
            List<Task> pushed = pushableOrNull(userId, ids, it.plannedStartAt(), it.plannedEndAt(), others);
            if (pushed == null) throw new CustomException(ErrorCode.TIMEBLOCK_CONFLICT);
            tasks.add(task);
            pushedPerItem.add(pushed);
        }

        // 전부 먼저 옮기고(잠금) 나서 밀려난 블록을 다시 배치한다.
        // (하나씩 옮기며 다시 배치하면 앞 항목에서 밀려난 블록이 뒤 항목의 새 자리에 들어갈 수 있다)
        for (int i = 0; i < items.size(); i++) {
            TaskBatchScheduleRequest.Item it = items.get(i);
            moveAndLog(userId, tasks.get(i), it.plannedStartAt(), it.plannedEndAt(), true);
        }
        Map<Long, Task> allPushed = new LinkedHashMap<>();
        pushedPerItem.forEach(list -> list.forEach(p -> allPushed.putIfAbsent(p.getTaskId(), p)));
        List<TaskScheduleResponse.Moved> moved = new ArrayList<>();
        List<Long> unscheduled = new ArrayList<>();
        replacePushed(userId, new ArrayList<>(allPushed.values()), moved, unscheduled);

        List<TaskScheduleResponse.CoveredFixed> covered = new ArrayList<>();
        for (TaskBatchScheduleRequest.Item it : items) {
            for (TaskScheduleResponse.CoveredFixed c : coveredFixed(userId, it.plannedStartAt(), it.plannedEndAt())) {
                if (covered.stream().noneMatch(x -> x.lifePatternId().equals(c.lifePatternId()))) covered.add(c);
            }
        }
        LocalDateTime now = clock.now();
        return new TaskBatchScheduleResponse(tasks.stream().map(t -> TimeBlockTaskResponse.from(t, now)).toList(),
                moved, unscheduled, covered);
    }

    private TaskScheduleResponse apply(Long userId, Task task, LocalDateTime start, LocalDateTime end,
                                       List<Task> pushed, boolean recordMove) {
        moveAndLog(userId, task, start, end, recordMove);
        List<TaskScheduleResponse.Moved> moved = new ArrayList<>();
        List<Long> unscheduled = new ArrayList<>();
        replacePushed(userId, pushed, moved, unscheduled);
        return new TaskScheduleResponse(TimeBlockTaskResponse.from(task, clock.now()), moved, unscheduled,
                coveredFixed(userId, start, end));
    }

    /** 옮기고 잠금 (옮기기 전 날짜·시각을 먼저 보관해 기록에 남긴다) */
    private void moveAndLog(Long userId, Task task, LocalDateTime start, LocalDateTime end, boolean recordMove) {
        LocalDate fromDate = TaskScheduleLogWriter.dateOf(task);
        LocalDateTime fromStart = task.getPlannedStartAt();
        task.scheduleByUser(start, end);
        if (recordMove) scheduleLogWriter.userMoved(userId, task, fromDate, fromStart);
    }

    /** 밀려난 블록 다시 배치 (그 블록이 원래 있던 날짜 기준) */
    private void replacePushed(Long userId, List<Task> pushed,
                               List<TaskScheduleResponse.Moved> moved, List<Long> unscheduled) {
        Map<LocalDate, Map<Long, LocalDateTime>> pushedByDate = new TreeMap<>();
        for (Task p : pushed) {
            if (!p.isPlaced()) continue;
            pushedByDate.computeIfAbsent(p.getPlannedStartAt().toLocalDate(), k -> new LinkedHashMap<>())
                    .put(p.getTaskId(), p.getPlannedStartAt());
            p.unplace();
        }
        pushedByDate.forEach((date, fromStarts) -> {
            PlacementOutcome out = dayPlacementService.placeDay(userId, date, PlacementMode.FILL,
                    fromStarts.keySet(), true);
            for (PlacementOutcome.PlacedBlock b : concat(out.placed(), out.movedToOtherDays())) {
                moved.add(new TaskScheduleResponse.Moved(b.taskId(), fromStarts.get(b.taskId()), b.start(), b.end(), b.reason()));
            }
            unscheduled.addAll(out.unscheduled());
        });
    }

    /** 이 구간이 덮은 고정 시간 */
    private List<TaskScheduleResponse.CoveredFixed> coveredFixed(Long userId, LocalDateTime start, LocalDateTime end) {
        DateTimeRange target = new DateTimeRange(start, end);
        List<TaskScheduleResponse.CoveredFixed> covered = new ArrayList<>();
        for (LocalDate d = start.toLocalDate(); !d.isAfter(end.minusNanos(1).toLocalDate()); d = d.plusDays(1)) {
            for (FixedBlock b : fixedBlockResolver.resolveDay(userId, d).displayBlocks()) {
                if (b.range().overlaps(target) && covered.stream().noneMatch(c -> c.lifePatternId().equals(b.lifePatternId()))) {
                    covered.add(new TaskScheduleResponse.CoveredFixed(b.lifePatternId(), b.title()));
                }
            }
        }
        return covered;
    }

    /** 시간 미정으로 되돌리기 (블록을 "시간 미정" 목록으로 끌어놓기) — 잠금도 해제 */
    public TimeBlockTaskResponse unschedule(Long userId, Long taskId) {
        Task task = taskFinder.findTaskWithDetails(taskId, userId);
        if (task.isRoutineTask() || task.isMissionTask()) {
            // 루틴·미션은 그날 인스턴스라 "시간 미정"으로 빼면 루틴 시간 정보가 사라진다 → 막는다
            throw new CustomException(ErrorCode.TIMEBLOCK_NOT_UNSCHEDULABLE);
        }
        LocalDate fromDate = TaskScheduleLogWriter.dateOf(task);
        LocalDateTime fromStart = task.getPlannedStartAt();
        task.unplace();
        scheduleLogWriter.userUnscheduled(userId, task, fromDate, fromStart);
        return TimeBlockTaskResponse.from(task, clock.now());
    }

    /**
     * 다른 날로 옮기기 (내일 / 모레 / 날짜 지정, 시간 미정끼리 드래그).
     *  - 시간이 정해진 Task + keepTime: 그날 같은 시각에 놓아 본다 (잠긴 일정과 겹치면 시간 미정으로)
     *  - 그 밖의 일반 Task: 그날 "시간 미정"으로
     *  - 루틴 Task 는 그날 인스턴스라 시간 미정이 될 수 없다 → 같은 시각으로만 옮기고, 자리가 없으면 409
     *  - 미션 Task 는 옮길 수 없다
     */
    public TimeBlockTaskResponse moveToDate(Long userId, Long taskId, TaskMoveDateRequest request) {
        LocalDate date = request.date();
        if (date == null || date.isBefore(clock.today())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        plannerSettingService.getOrCreateForUpdate(userId); // 같은 사용자의 배치와 동시에 돌지 않게
        Task task = findLive(userId, taskId);
        if (task.isMissionTask() || task.getTaskStatus() == TaskStatus.COMPLETED) {
            throw new CustomException(ErrorCode.TIMEBLOCK_NOT_UNSCHEDULABLE);
        }

        LocalDateTime start = task.getPlannedStartAt();
        LocalDateTime end = task.getPlannedEndAt();
        if (start != null && end != null && (request.keepTimeOrDefault() || task.isRoutineTask())) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(start.toLocalDate(), date);
            if (days == 0) return TimeBlockTaskResponse.from(task, clock.now());
            LocalDateTime newStart = start.plusDays(days);
            LocalDateTime newEnd = end.plusDays(days);
            if (tryReschedule(userId, taskId, newStart, newEnd) != null) {
                return TimeBlockTaskResponse.from(task, clock.now());
            }
            if (task.isRoutineTask()) throw new CustomException(ErrorCode.TIMEBLOCK_CONFLICT);
        }
        if (task.isRoutineTask()) {
            throw new CustomException(ErrorCode.TIMEBLOCK_NOT_UNSCHEDULABLE);
        }
        if (task.isPlaced()) unschedule(userId, taskId);
        if (task.isCarryPending()) task.acceptCarry(); // 사용자가 직접 정한 날이라 이월 제안은 끝
        task.moveUnscheduledTo(date, null);
        return TimeBlockTaskResponse.from(task, clock.now());
    }

    // ── 22시 이월 제안에 답하기 ("어제 못한 ○○를 ○시로 옮길까요?") ──────────

    /** 옮기기 — 엔진이 잡아 둔 자리 그대로 확정 */
    public TimeBlockTaskResponse acceptCarry(Long userId, Long taskId) {
        Task task = findLive(userId, taskId);
        task.acceptCarry();
        return TimeBlockTaskResponse.from(task, clock.now());
    }

    /** 안 옮기기 — 시간을 비우고 그날 "시간 미정"으로 (직접 시간을 정하거나 지울 수 있게) */
    public TimeBlockTaskResponse declineCarry(Long userId, Long taskId) {
        Task task = findLive(userId, taskId);
        task.declineCarry();
        return TimeBlockTaskResponse.from(task, clock.now());
    }

    /**
     * 이월 되돌리기 (2026-10) — 옮겨진 Task 를 원래 날짜 · 원래 시각으로 돌려놓는다.
     * 원래 시각에 다른 일정이 꽉 차 있으면 그날 "시간 미정"으로 둔다. 되돌린 Task 는 자동 이월이 다시 옮기지 않는다.
     * "그때 했어요"는 화면이 되돌린 뒤 실제 시간 입력(완료)으로 이어간다.
     */
    public TimeBlockTaskResponse revertCarry(Long userId, Long taskId) {
        return revertCarry(userId, taskId, null);
    }

    /**
     * @param from 되돌아갈 날 (누른 이월 흔적이 있는 날). null 이면 마지막 이월의 원래 날.
     *             이미 그날에 있으면 아무것도 바꾸지 않고 지금 상태를 돌려준다 (두 번 눌러도 오류 없이 — "그때 했어요"가 이어서 실제 시간 입력)
     */
    public TimeBlockTaskResponse revertCarry(Long userId, Long taskId, LocalDate from) {
        plannerSettingService.getOrCreateForUpdate(userId); // 같은 사용자의 배치와 동시에 돌지 않게
        Task task = findLive(userId, taskId);
        LocalDate target = from != null ? from : task.getCarriedFromDate();
        if (target == null) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        if (target.equals(task.currentDate())) return TimeBlockTaskResponse.from(task, clock.now());

        // 그날에서 옮겨 간 기록 (원래 시각). 흔적이 없는 날로는 되돌리지 않는다
        var log = scheduleLogRepository == null ? java.util.Optional.<com.naleul.naleul.domain.pattern.entity.TaskScheduleLog>empty()
                : scheduleLogRepository.findTopByTaskIdAndActionAndFromDateOrderByTaskScheduleLogIdDesc(
                        taskId, TaskScheduleAction.CARRY_OVER, target);
        if (from != null && scheduleLogRepository != null && log.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        LocalDateTime originalStart = log.map(l -> l.getFromStartAt())
                .filter(s -> s.toLocalDate().equals(target))
                .orElse(null);
        Long minutes = task.getPlannedDurationMinutes();
        task.revertCarryTo(target);
        if (originalStart != null) {
            LocalDateTime end = originalStart.plusMinutes(minutes != null && minutes > 0 ? minutes : 30);
            List<Task> pushed = pushableOrNull(userId, taskId, originalStart, end);
            if (pushed != null) {
                apply(userId, task, originalStart, end, pushed, true);
            }
        }
        return TimeBlockTaskResponse.from(task, clock.now());
    }

    /** 그날 이월 제안 모두 옮기기 */
    public int acceptAllCarry(Long userId, LocalDate date) {
        int n = 0;
        for (Task t : placementQueryRepository.findForPlacement(userId, date, date.atStartOfDay(), date.plusDays(1).atStartOfDay())) {
            if (!t.isCarryPending()) continue;
            t.acceptCarry();
            n++;
        }
        return n;
    }

    /** 잠금 해제 → 다음 자동 배치("다시 배치", 22시 생성) 때 옮겨질 수 있음 */
    public TimeBlockTaskResponse unlock(Long userId, Long taskId) {
        Task task = taskFinder.findTaskWithDetails(taskId, userId);
        task.unlock();
        return TimeBlockTaskResponse.from(task, clock.now());
    }

    /** 우선순위만 바꾼다 — 배치 · 일정 로그는 건드리지 않음 (다음 자동 배치부터 중요도 점수에 반영) */
    public TimeBlockTaskResponse changePriority(Long userId, Long taskId, TaskPriority priority) {
        Task task = taskFinder.findTaskWithDetails(taskId, userId);
        task.changePriority(priority);
        return TimeBlockTaskResponse.from(task, clock.now());
    }

    @Transactional(readOnly = true)
    public TaskQuotaResponse quota(Long userId) {
        User user = userFinder.findUser(userId);
        long used = taskQuotaValidator.used(user, clock.today());
        boolean premium = user.isCurrentlyPremium();
        return new TaskQuotaResponse(premium,
                premium ? null : PlanLimits.display(PlanLimits.TASK_PER_DAY_FREE_LIMIT),
                used,
                taskQuotaValidator.remaining(user));
    }

    // ── 내부 ─────────────────────────────────────────────

    /** 엔진이 놓은 미완료 일회성/루틴 블록 = 밀어낼 수 있음 */
    static boolean isMovableEngineBlock(Task t) {
        return !t.isScheduleLocked()
                && t.getPlacedBy() == PlacedBy.ENGINE
                && t.getTaskStatus() == TaskStatus.TODO
                && !t.isMissionTask();
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> r = new ArrayList<>(a);
        r.addAll(b);
        return r;
    }
}
