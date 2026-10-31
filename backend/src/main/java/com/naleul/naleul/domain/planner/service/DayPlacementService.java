package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.habit.service.HabitService;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.lifePattern.service.FixedBlockResolver;
import com.naleul.naleul.domain.lifePattern.support.DayFixedBlocks;
import com.naleul.naleul.domain.lifePattern.support.FixedBlock;
import com.naleul.naleul.domain.pattern.service.TaskScheduleLogWriter;
import com.naleul.naleul.domain.planner.engine.*;
import com.naleul.naleul.domain.planner.engine.PlacementCandidate.Window;
import com.naleul.naleul.domain.planner.engine.PlacementInput.BusyTask;
import com.naleul.naleul.domain.planner.entity.PlannerSetting;
import com.naleul.naleul.domain.planner.repository.PlacementQueryRepository;
import com.naleul.naleul.domain.planner.service.PlacementOutcome.PlacedBlock;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.PlacedBy;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 하루 배치 (명세 6장): DB 에서 그날 상황을 읽어 엔진을 돌리고 결과를 Task 에 저장한다.
 *
 * Task 분류
 *  - 자리만 차지 (busy): 잠긴 Task, 완료된 Task, 미션 Task, 사용자가 정한 시간, (FILL 모드의) 기존 엔진 배치
 *  - 배치 대상 (candidate): 시간 없는 TODO Task + 모드에 따라 비운 엔진 배치 Task
 *  - 무시: 건너뜀(SKIPPED), 삭제·완료된 목표의 Task
 *
 * 사용자가 정한 Task 가 고정 시간을 덮는 경우(저녁 약속)는 그대로 둔다 (명세 v0.2).
 * 같은 사용자의 배치가 동시에 돌지 않도록 planner_setting 행을 잠그고 시작한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DayPlacementService {

    static final int DEFAULT_DURATION_MINUTES = 30;
    /** 마감일까지 다른 날로 넘겨볼 최대 일수 */
    static final int MAX_OVERFLOW_DAYS = 7;
    /** 오늘 배치할 때 지금부터 비워 두는 준비 시간 (명세 6-2: 지금 + 10분) */
    static final int PREP_MINUTES = 10;

    private final PlacementQueryRepository queryRepository;
    private final FixedBlockResolver fixedBlockResolver;
    private final PlacementContextLoader contextLoader;
    private final PlannerSettingService plannerSettingService;
    private final PlannerClock clock;
    /** 나의 패턴: 엔진 배치 기록 (AI 배치 수정률 계산용, 명세 4 · 6-2) */
    private final TaskScheduleLogWriter scheduleLogWriter;
    /** 완료한 Task 는 실제로 한 시간이 자리를 차지한다. 단위 테스트처럼 없으면 계획 시각을 쓴다 */
    @Autowired(required = false)
    private ActualRangeReader actualRangeReader;
    /** 시간대를 정한 습관 시간은 비워 둔다 (V56). 단위 테스트처럼 없으면 무시 */
    @Autowired(required = false)
    private HabitService habitService;
    /** 나눠 넣은 Task 의 계획 구간 (V65). 단위 테스트처럼 없으면 나눈 구간을 저장하지 않는다 */
    @Autowired(required = false)
    private PlannedSegmentStore segmentStore;

    /**
     * @param onlyTaskIds  null 이 아니면 이 Task 들만 배치 대상으로 삼는다 (나머지 미배치 Task 는 건드리지 않음)
     * @param allowOverflow 못 넣은 일회성 Task 를 다음 날(마감일까지)로 넘겨볼지
     */
    @Transactional
    public PlacementOutcome placeDay(Long userId, LocalDate date, PlacementMode mode,
                                     Set<Long> onlyTaskIds, boolean allowOverflow) {
        PlannerSetting setting = plannerSettingService.getOrCreateForUpdate(userId);
        LocalDate today = clock.today();
        if (date.isBefore(today)) {
            return PlacementOutcome.empty(date); // 지난 날짜는 배치하지 않음
        }
        // 오늘이면 "지금 + 준비 시간" 이후부터 (21:00 에 확정했는데 21:00 시작 블록이 생기지 않게)
        LocalDateTime notBefore = date.equals(today) ? clock.now().plusMinutes(PREP_MINUTES) : null;

        PlacementOutcome outcome = PlacementOutcome.mutable(date);
        Set<Long> unplacedIds = placeOnce(userId, date, mode, onlyTaskIds, notBefore, setting.getTaskGapMinutes(),
                today, outcome.placed(), null);

        for (Long taskId : unplacedIds) {
            PlacedBlock moved = allowOverflow ? overflow(userId, taskId, date, setting.getTaskGapMinutes(), today) : null;
            if (moved != null) outcome.movedToOtherDays().add(moved);
            else outcome.unscheduled().add(taskId);
        }
        log.info("[Placement] userId={} date={} mode={} placed={} moved={} unscheduled={}",
                userId, date, mode, outcome.placed().size(), outcome.movedToOtherDays().size(), outcome.unscheduled().size());
        return outcome;
    }

    /** 기간의 고정 시간 충돌 정리 (생활 패턴 변경 후) — 날짜마다 별도 트랜잭션은 호출하는 쪽이 보장 */
    @Transactional
    public PlacementOutcome repairDay(Long userId, LocalDate date) {
        return placeDay(userId, date, PlacementMode.REPAIR, null, false);
    }

    // ── 한 날짜 배치 ──────────────────────────────────────

    /**
     * @param movedFrom 다른 날에서 넘어온 Task 면 원래 날짜 (이유 문장 앞에 "…옮겼어요." 를 붙임)
     * @return 못 넣은 Task id
     */
    private Set<Long> placeOnce(Long userId, LocalDate date, PlacementMode mode, Set<Long> onlyTaskIds,
                                LocalDateTime notBefore, int gapMinutes, LocalDate today,
                                List<PlacedBlock> sink, LocalDate movedFrom) {
        DayFixedBlocks fixed = fixedBlockResolver.resolveDay(userId, date);
        DateTimeRange dayRange = fixed.dayRange();

        LocalDateTime queryStart = min(date.atStartOfDay(), dayRange.start());
        LocalDateTime queryEnd = max(date.plusDays(1).atStartOfDay(), dayRange.end());
        List<Task> tasks = new ArrayList<>(queryRepository.findForPlacement(userId, date, queryStart, queryEnd));
        if (onlyTaskIds != null && movedFrom != null) {
            // 다른 날에서 넘어온 Task 는 아직 그 날짜 소속이라 위 조회에 없음 → 따로 추가
            for (Long id : onlyTaskIds) {
                if (tasks.stream().noneMatch(t -> id.equals(t.getTaskId()))) {
                    tasks.addAll(queryRepository.findOneForPlacement(userId, id));
                }
            }
        }

        List<DateTimeRange> fixedRanges = new ArrayList<>();
        for (FixedBlock b : fixed.busyBlocks()) fixedRanges.add(b.range());
        // 시간대를 정한 습관(예: 샤워 20:00~20:30)도 그 시간은 비워 둔다 (V56)
        if (habitService != null) {
            for (HabitService.HabitSlot h : habitService.timedSlots(userId, date, date)) {
                fixedRanges.add(new DateTimeRange(h.start(), h.end()));
            }
        }

        List<BusyTask> busy = new ArrayList<>();
        // 완료한 Task: 계획 자리가 아니라 실제로 한 자리를 막는다 (계획 자리는 화면에서도 비어 보이므로 쓸 수 있어야 함)
        Map<Long, DateTimeRange> actuals = actualRangeReader != null ? actualRangeReader.actualRanges(tasks) : Map.of();
        if (actualRangeReader != null) {
            Set<Long> loaded = new HashSet<>();
            tasks.forEach(t -> loaded.add(t.getTaskId()));
            // 계획은 다른 날(시간)인데 실제로는 이 날 한 완료 Task
            List<Task> doneHere = actualRangeReader.completedWithActualIn(userId, queryStart, queryEnd).stream()
                    .filter(done -> !loaded.contains(done.getTaskId()))
                    .toList();
            Map<Long, DateTimeRange> doneActuals = actualRangeReader.actualRanges(doneHere);
            for (Task done : doneHere) {
                DateTimeRange r = doneActuals.get(done.getTaskId());
                if (r != null) busy.add(new BusyTask(r, goalIdOf(done)));
            }
        }
        // 나눠 넣은 Task 는 구간마다 자리를 차지한다 (구간 사이 점심 시간 등은 비어 있음)
        Map<Long, List<DateTimeRange>> splitRanges = segmentStore != null ? segmentStore.segmentsOf(tasks) : Map.of();
        List<Task> candidates = new ArrayList<>();
        // 다시 배치하려고 비운 엔진 블록의 원래 시각 (기록용 — unplace() 하면 시각이 사라지므로 먼저 보관)
        Map<Long, LocalDateTime> freedFrom = new HashMap<>();

        for (Task t : tasks) {
            if (t.getTaskStatus() == TaskStatus.SKIPPED || t.getTaskStatus() == TaskStatus.DELETED) continue;
            boolean isCandidateScope = onlyTaskIds == null || onlyTaskIds.contains(t.getTaskId());

            if (t.isPlaced()) {
                DateTimeRange r = rangeOf(t);
                if (shouldFreeEnginePlacement(t, mode, r, notBefore, fixedRanges, dayRange, splitRanges.get(t.getTaskId()))
                        && isCandidateScope) {
                    freedFrom.put(t.getTaskId(), t.getPlannedStartAt());
                    t.unplace();
                    if (segmentStore != null) segmentStore.clear(t.getTaskId());
                    if (isPlaceable(t)) candidates.add(t);
                } else if (r != null && splitRanges.containsKey(t.getTaskId()) && t.getTaskStatus() == TaskStatus.TODO) {
                    for (DateTimeRange seg : splitRanges.get(t.getTaskId())) busy.add(new BusyTask(seg, goalIdOf(t)));
                } else if (r != null) {
                    DateTimeRange occupied = ActualRangeReader.occupied(t, actuals.get(t.getTaskId()), DEFAULT_DURATION_MINUTES);
                    busy.add(new BusyTask(occupied != null ? occupied : r, goalIdOf(t)));
                }
            } else if (isPlaceable(t) && isCandidateScope && date.equals(t.getRoutineDate())
                    && (mode != PlacementMode.REPAIR || onlyTaskIds != null)) {
                candidates.add(t);
            } else if (isPlaceable(t) && isCandidateScope && movedFrom != null) {
                candidates.add(t); // 다른 날에서 넘어온 Task
            }
        }
        if (candidates.isEmpty()) return Set.of();

        Map<Long, Task> byId = new HashMap<>();
        candidates.forEach(t -> byId.put(t.getTaskId(), t));
        PlacementInput input = new PlacementInput(date, dayRange, notBefore, fixedRanges, busy,
                buildCandidates(candidates, date, today), contextLoader.efficiency(userId, today), gapMinutes,
                contextLoader.evidence(userId, today));

        PlacementResult result = TimeBlockPlacementEngine.place(input);

        for (PlacementResult.Placed p : result.placed()) {
            Task t = byId.get(p.taskId());
            String reason = movedFrom != null
                    ? ReasonComposer.movedPrefix(movedFrom, date, today) + p.reasonText()
                    : p.reasonText();
            String codes = movedFrom != null ? p.reasonCodesCsv() + "," + ReasonCode.MOVED_TO_OTHER_DAY : p.reasonCodesCsv();
            t.placeByEngine(p.start(), p.end(), truncate(reason, 500), truncate(codes, 200));
            if (p.split()) {
                // 첫 시작 ~ 마지막 끝으로 잡히면 길이가 구간 사이 시간만큼 늘어나 보이므로 구간 합계로 되돌린다
                t.markPlannedSplit(p.segments().stream().mapToLong(DateTimeRange::minutes).sum());
                if (segmentStore != null) segmentStore.replace(t.getTaskId(), p.segments());
            }
            scheduleLogWriter.enginePlaced(userId, t, freedFrom.get(t.getTaskId()));
            sink.add(new PlacedBlock(t.getTaskId(), date, p.start(), p.end(), t.getPlacementReason()));
        }
        Set<Long> unplaced = new LinkedHashSet<>();
        result.unplaced().forEach(u -> unplaced.add(u.taskId()));
        return unplaced;
    }

    /**
     * 엔진이 놓았던 블록을 비우고 다시 배치할지.
     *  REPLAN: 아직 시작 전인 엔진 블록 전부
     *  REPAIR: 고정 시간과 겹치거나 하루 범위를 벗어난 엔진 블록
     *  FILL  : 비우지 않음
     */
    static boolean shouldFreeEnginePlacement(Task t, PlacementMode mode, DateTimeRange r, LocalDateTime notBefore,
                                             List<DateTimeRange> fixedRanges, DateTimeRange dayRange) {
        return shouldFreeEnginePlacement(t, mode, r, notBefore, fixedRanges, dayRange, null);
    }

    /** segments 가 있으면(나눠 넣은 Task) 고정 시간과 겹치는지를 구간마다 본다 — 구간 사이 점심 시간은 겹침이 아니다 */
    static boolean shouldFreeEnginePlacement(Task t, PlacementMode mode, DateTimeRange r, LocalDateTime notBefore,
                                             List<DateTimeRange> fixedRanges, DateTimeRange dayRange,
                                             List<DateTimeRange> segments) {
        if (t.isScheduleLocked() || t.getPlacedBy() != PlacedBy.ENGINE) return false;
        if (t.getTaskStatus() != TaskStatus.TODO || t.isMissionTask()) return false;
        if (r == null) return false;
        if (notBefore != null && r.start().isBefore(notBefore)) return false; // 이미 시작했거나 지난 블록은 유지
        return switch (mode) {
            case FILL -> false;
            case REPLAN -> true;
            case REPAIR -> r.start().isBefore(dayRange.start()) || r.end().isAfter(dayRange.end())
                    || (segments != null && !segments.isEmpty()
                        ? segments.stream().anyMatch(seg -> fixedRanges.stream().anyMatch(seg::overlaps))
                        : fixedRanges.stream().anyMatch(r::overlaps));
        };
    }

    /** 배치 대상이 될 수 있는 Task: TODO, 잠기지 않음, 미션 아님, 목표가 삭제·완료되지 않음 */
    static boolean isPlaceable(Task t) {
        if (t.getTaskStatus() != TaskStatus.TODO || t.isScheduleLocked() || t.isMissionTask()) return false;
        GoalCategory g = t.getGoalCategory();
        return g == null || (g.getGoalCategoryStatus() != GoalCategoryStatus.DELETED
                && g.getGoalCategoryStatus() != GoalCategoryStatus.COMPLETED);
    }

    private List<PlacementCandidate> buildCandidates(List<Task> tasks, LocalDate date, LocalDate today) {
        Map<Long, Integer> plannedByRoutine = new HashMap<>();
        Set<Long> goalIds = new HashSet<>();
        for (Task t : tasks) {
            if (t.getRoutine() != null) plannedByRoutine.put(t.getRoutine().getRoutineId(), t.durationMinutesOr(DEFAULT_DURATION_MINUTES));
            Long g = goalIdOf(t);
            if (g != null) goalIds.add(g);
        }
        Map<Long, Integer> adjusted = contextLoader.adjustedRoutineMinutes(plannedByRoutine, today);
        Map<Long, List<Window>> windows = contextLoader.preferredWindows(goalIds);

        List<PlacementCandidate> result = new ArrayList<>();
        for (Task t : tasks) {
            int original = t.durationMinutesOr(DEFAULT_DURATION_MINUTES);
            int duration = t.getRoutine() != null
                    ? adjusted.getOrDefault(t.getRoutine().getRoutineId(), original)
                    : original;
            Importance imp = ImportanceScorer.score(t.getTaskPriority(), t.getDueDate(), t.getTaskKind(),
                    t.getCarryOverCount(), t.getMilestone() != null ? t.getMilestone().getDueDate() : null, date);
            Long goalId = goalIdOf(t);
            // 약속 일정은 나눠 넣지 않는다 (만나는 시간이 둘로 쪼개지면 안 되므로)
            result.add(new PlacementCandidate(t.getTaskId(), goalId, duration, original, imp,
                    goalId != null ? windows.getOrDefault(goalId, List.of()) : List.of(),
                    t.getTaskKind() != com.naleul.naleul.domain.task.enums.TaskKind.APPOINTMENT));
        }
        return result;
    }

    // ── 다른 날로 넘기기 (명세 6-4) ────────────────────────

    /**
     * 일회성 Task 만 넘긴다 (루틴은 그날 인스턴스라 날짜를 바꾸지 않음).
     * 마감일이 있으면 다음 날 ~ 마감일(최대 7일) 순서로, 없으면 다음 날 하루만 시도.
     */
    private PlacedBlock overflow(Long userId, Long taskId, LocalDate from, int gapMinutes, LocalDate today) {
        Task task = queryRepository.findOneForPlacement(userId, taskId).stream().findFirst().orElse(null);
        if (task == null || !task.isOneOff() || task.isPlaced()) return null;

        LocalDate last = from.plusDays(1);
        if (task.getDueDate() != null && task.getDueDate().isAfter(from)) {
            last = task.getDueDate().isBefore(from.plusDays(MAX_OVERFLOW_DAYS))
                    ? task.getDueDate() : from.plusDays(MAX_OVERFLOW_DAYS);
        }
        for (LocalDate d = from.plusDays(1); !d.isAfter(last); d = d.plusDays(1)) {
            List<PlacedBlock> sink = new ArrayList<>();
            placeOnce(userId, d, PlacementMode.FILL, Set.of(taskId), null, gapMinutes, today, sink, from);
            if (!sink.isEmpty()) return sink.get(0);
        }
        return null;
    }

    // ── 유틸 ─────────────────────────────────────────────

    private static DateTimeRange rangeOf(Task t) {
        LocalDateTime s = t.getPlannedStartAt();
        LocalDateTime e = t.getPlannedEndAt();
        if (s == null) return null;
        if (e == null || !e.isAfter(s)) e = s.plusMinutes(t.durationMinutesOr(DEFAULT_DURATION_MINUTES));
        return new DateTimeRange(s, e);
    }

    private static Long goalIdOf(Task t) {
        return t.getGoalCategory() != null ? t.getGoalCategory().getGoalCategoryId() : null;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
