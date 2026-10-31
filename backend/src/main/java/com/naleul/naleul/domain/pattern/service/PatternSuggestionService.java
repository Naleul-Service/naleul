package com.naleul.naleul.domain.pattern.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naleul.naleul.domain.pattern.calculator.ConflictChecker;
import com.naleul.naleul.domain.pattern.calculator.RoutineInfo;
import com.naleul.naleul.domain.pattern.dto.PatternReportResponse.Change;
import com.naleul.naleul.domain.pattern.dto.PatternSuggestionApplyResponse;
import com.naleul.naleul.domain.pattern.dto.PatternSuggestionApplyResponse.RePlaced;
import com.naleul.naleul.domain.pattern.entity.PatternSuggestionAction;
import com.naleul.naleul.domain.pattern.repository.PatternSuggestionActionRepository;
import com.naleul.naleul.domain.pattern.repository.TaskScheduleLogRepository;
import com.naleul.naleul.domain.pattern.support.SuggestionKey;
import com.naleul.naleul.domain.planner.repository.PlacementQueryRepository;
import com.naleul.naleul.domain.planner.service.DayPlacementService;
import com.naleul.naleul.domain.planner.service.PlacementMode;
import com.naleul.naleul.domain.planner.service.PlacementOutcome;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.PlacedBy;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 제안 반영 / 미루기 (명세 4 · 4-3, 4-4).
 *
 * 반영은 "내일부터"만 바꾼다. 오늘 이전 Task 와 완료한 Task 는 건드리지 않아 지난 기록이 패턴 분석에 남는다.
 * (RoutineService.update 를 쓰지 않는 이유: 그쪽은 오늘 Task 부터 다시 만든다)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PatternSuggestionService {

    /** 반영 후 겹치는 자동 배치 블록을 다시 배치할 기간 (그 뒤 날짜는 22시 작업이 매일 처리) */
    static final int REPAIR_DAYS = 7;
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final RoutineRepository routineRepository;
    private final TaskRepository taskRepository;
    private final TaskScheduleLogRepository taskScheduleLogRepository;
    private final PatternSuggestionActionRepository suggestionActionRepository;
    private final PatternConflictCheckerFactory conflictCheckerFactory;
    private final PlacementQueryRepository placementQueryRepository;
    private final DayPlacementService dayPlacementService;
    private final PatternReportService patternReportService;
    private final ObjectMapper objectMapper;
    private final PlannerClock clock;

    public PatternSuggestionApplyResponse apply(Long userId, String rawKey) {
        SuggestionKey key = parse(rawKey);
        Routine routine = findOwnedRoutine(userId, key.routineId());
        RoutineInfo info = PatternReportService.toInfo(routine);
        LocalDate tomorrow = clock.today().plusDays(1);
        if (!info.active() || info.repeatEndDate() == null || info.repeatEndDate().isBefore(tomorrow)) {
            throw new CustomException(ErrorCode.PATTERN_SUGGESTION_STALE);
        }
        ConflictChecker checker = conflictCheckerFactory.create(userId, tomorrow);

        Result result = switch (key.type()) {
            case ROUTINE_TIME -> applyTime(routine, info, key, tomorrow, checker);
            case ROUTINE_DAY -> applyDay(routine, info, key, tomorrow, checker);
            // 목표 주간 점검 제안(V47)은 GoalAdjustmentService 가 처리해요 — 패턴 제안 키로는 들어올 수 없어요
            case LOAD_DOWN, LOAD_UP -> throw new CustomException(ErrorCode.PATTERN_SUGGESTION_NOT_FOUND);
        };

        suggestionActionRepository.save(PatternSuggestionAction.applied(userId, key.raw(), key.type(),
                routine.getRoutineId(), json(result.before()), json(result.after()), clock.now()));

        List<RePlaced> rePlaced = new ArrayList<>();
        List<Long> unscheduled = new ArrayList<>();
        repairEngineBlocks(userId, routine.getRoutineId(), result.changedDates(), tomorrow, rePlaced, unscheduled);

        patternReportService.evictCache(userId); // 루틴이 바뀌었으니 오늘 리포트를 다시 계산
        log.info("[Pattern] 제안 반영 userId={} key={} tasks={} rePlaced={}",
                userId, key.raw(), result.affected(), rePlaced.size());
        return new PatternSuggestionApplyResponse(key.raw(), key.type(), routine.getRoutineId(), routine.getRoutineName(),
                result.before(), result.after(), result.affected(), rePlaced, unscheduled);
    }

    public void dismiss(Long userId, String rawKey) {
        SuggestionKey key = parse(rawKey);
        Routine routine = findOwnedRoutine(userId, key.routineId());
        suggestionActionRepository.save(PatternSuggestionAction.dismissed(userId, key.raw(), key.type(),
                routine.getRoutineId(), clock.now()));
        patternReportService.evictCache(userId);
    }

    // ── 루틴 시간 변경 ────────────────────────────────────

    private Result applyTime(Routine routine, RoutineInfo info, SuggestionKey key, LocalDate tomorrow,
                             ConflictChecker checker) {
        if (routine.getRepeatStartTime() == null || routine.getRepeatStartTime().equals(key.time())) {
            throw new CustomException(ErrorCode.PATTERN_SUGGESTION_STALE);
        }
        LocalTime newStart = key.time();
        LocalTime newEnd = newStart.plusMinutes(info.duration());
        if (!newEnd.isAfter(newStart)) {
            throw new CustomException(ErrorCode.PATTERN_SUGGESTION_STALE); // 자정을 넘기게 되는 변경은 하지 않음
        }
        if (checker.conflictRatio(info, info.effectiveDays(), newStart, newEnd) >= 0.5) {
            throw new CustomException(ErrorCode.PATTERN_SUGGESTION_STALE);
        }
        Change before = new Change(fmt(routine.getRepeatStartTime()), fmt(routine.getRepeatEndTime()), null);
        routine.update(null, null, null, null, null, newStart, newEnd, null);

        List<Task> future = futureTodoTasks(routine, tomorrow);
        Set<Long> movedByUser = movedByUser(future);
        Set<LocalDate> changed = new TreeSet<>();
        int affected = 0;
        for (Task t : future) {
            if (movedByUser.contains(t.getTaskId())) continue; // 그날만 직접 옮긴 Task 는 그대로
            LocalDate d = t.getRoutineDate();
            t.scheduleByUser(d.atTime(newStart), d.atTime(newEnd));
            changed.add(d);
            affected++;
        }
        return new Result(before, new Change(fmt(newStart), fmt(newEnd), null), affected, changed);
    }

    // ── 루틴 요일 변경 ────────────────────────────────────

    private Result applyDay(Routine routine, RoutineInfo info, SuggestionKey key, LocalDate tomorrow,
                            ConflictChecker checker) {
        Set<DayOfWeek> days = info.days() == null ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(info.days());
        if (days.isEmpty() || !days.contains(key.fromDay()) || days.contains(key.toDay())) {
            throw new CustomException(ErrorCode.PATTERN_SUGGESTION_STALE);
        }
        if (info.start() != null && checker.conflictRatio(info, EnumSet.of(key.toDay()), info.start(),
                info.start().plusMinutes(info.duration())) >= 0.5) {
            throw new CustomException(ErrorCode.PATTERN_SUGGESTION_STALE);
        }
        Change before = new Change(null, null, sorted(days));
        Set<DayOfWeek> newDays = EnumSet.copyOf(days);
        newDays.remove(key.fromDay());
        newDays.add(key.toDay());
        routine.updateRepeatDays(sorted(newDays));

        // 빠진 요일의 내일 이후 미완료 Task 삭제 (기존 루틴 동기화와 같은 방식 — 물리 삭제)
        List<Task> future = futureTodoTasks(routine, tomorrow);
        List<Task> removed = future.stream().filter(t -> t.getRoutineDate().getDayOfWeek() == key.fromDay()).toList();
        taskRepository.deleteAll(removed);

        // 새 요일의 내일 이후 Task 생성 (이미 그 날짜 Task 가 있으면 건너뜀)
        Set<LocalDate> existing = new HashSet<>();
        taskRepository.findByRoutine(routine).forEach(t -> { if (t.getRoutineDate() != null) existing.add(t.getRoutineDate()); });
        removed.forEach(t -> existing.remove(t.getRoutineDate()));
        LocalDate from = routine.getRepeatStartDate().isAfter(tomorrow) ? routine.getRepeatStartDate() : tomorrow;
        List<Task> created = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(routine.getRepeatEndDate()); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == key.toDay() && !existing.contains(d)) created.add(Task.fromRoutine(routine, d));
        }
        taskRepository.saveAll(created);

        Set<LocalDate> changed = new TreeSet<>();
        created.forEach(t -> changed.add(t.getRoutineDate()));
        return new Result(before, new Change(null, null, sorted(newDays)), removed.size() + created.size(), changed);
    }

    // ── 공통 ──────────────────────────────────────────────

    /**
     * 바뀐 루틴 블록과 겹치는 자동 배치 블록을 비우고 그날 빈 시간에 다시 배치 (없으면 마감일 안의 다음 날).
     * 드래그(TaskScheduleService)에서 밀려난 블록을 처리하는 방식과 같다.
     */
    private void repairEngineBlocks(Long userId, Long routineId, Set<LocalDate> dates, LocalDate tomorrow,
                                    List<RePlaced> rePlaced, List<Long> unscheduled) {
        LocalDate last = tomorrow.plusDays(REPAIR_DAYS - 1L);
        for (LocalDate date : dates) {
            if (date.isAfter(last)) break;
            LocalDateTime dayStart = date.atStartOfDay(), dayEnd = date.plusDays(1).atStartOfDay();
            List<Task> tasks = placementQueryRepository.findForPlacement(userId, date, dayStart, dayEnd);
            List<DateTimeRange> routineBlocks = tasks.stream()
                    .filter(t -> t.getRoutine() != null && routineId.equals(t.getRoutine().getRoutineId()))
                    .filter(t -> t.getPlannedStartAt() != null && t.getPlannedEndAt() != null
                            && t.getPlannedEndAt().isAfter(t.getPlannedStartAt()))
                    .map(t -> new DateTimeRange(t.getPlannedStartAt(), t.getPlannedEndAt()))
                    .toList();
            if (routineBlocks.isEmpty()) continue;

            Map<Long, LocalDateTime> fromStarts = new LinkedHashMap<>();
            for (Task t : tasks) {
                if (t.isScheduleLocked() || t.getPlacedBy() != PlacedBy.ENGINE || t.getTaskStatus() != TaskStatus.TODO) continue;
                if (t.getPlannedStartAt() == null || t.getPlannedEndAt() == null) continue;
                DateTimeRange r = new DateTimeRange(t.getPlannedStartAt(), t.getPlannedEndAt());
                if (routineBlocks.stream().anyMatch(b -> b.overlaps(r))) {
                    fromStarts.put(t.getTaskId(), t.getPlannedStartAt());
                    t.unplace();
                }
            }
            if (fromStarts.isEmpty()) continue;
            PlacementOutcome out = dayPlacementService.placeDay(userId, date, PlacementMode.FILL, fromStarts.keySet(), true);
            out.placed().forEach(b -> rePlaced.add(new RePlaced(b.taskId(), fromStarts.get(b.taskId()), b.start())));
            out.movedToOtherDays().forEach(b -> rePlaced.add(new RePlaced(b.taskId(), fromStarts.get(b.taskId()), b.start())));
            unscheduled.addAll(out.unscheduled());
        }
    }

    private List<Task> futureTodoTasks(Routine routine, LocalDate tomorrow) {
        return taskRepository.findByRoutine(routine).stream()
                .filter(t -> t.getTaskStatus() == TaskStatus.TODO)
                .filter(t -> t.getRoutineDate() != null && !t.getRoutineDate().isBefore(tomorrow))
                .toList();
    }

    private Set<Long> movedByUser(List<Task> tasks) {
        List<Long> ids = tasks.stream().map(Task::getTaskId).toList();
        Set<Long> moved = new HashSet<>();
        for (int i = 0; i < ids.size(); i += 1000) {
            moved.addAll(taskScheduleLogRepository.findTaskIdsMovedByUser(ids.subList(i, Math.min(ids.size(), i + 1000))));
        }
        return moved;
    }

    private Routine findOwnedRoutine(Long userId, Long routineId) {
        return routineRepository.findAllByRoutineIdInWithRepeatDays(List.of(routineId)).stream()
                .filter(r -> r.getUser() != null && Objects.equals(r.getUser().getUserId(), userId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.PATTERN_SUGGESTION_NOT_FOUND));
    }

    private static SuggestionKey parse(String raw) {
        return SuggestionKey.parse(raw).orElseThrow(() -> new CustomException(ErrorCode.PATTERN_SUGGESTION_NOT_FOUND));
    }

    private String json(Change c) {
        try {
            return objectMapper.writeValueAsString(c);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String fmt(LocalTime t) {
        return t != null ? t.format(HHMM) : null;
    }

    private static List<DayOfWeek> sorted(Set<DayOfWeek> days) {
        return days.stream().sorted().toList();
    }

    private record Result(Change before, Change after, int affected, Set<LocalDate> changedDates) {
    }
}
