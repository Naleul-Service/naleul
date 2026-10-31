package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.habit.service.HabitService;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.lifePattern.dto.response.DayFixedBlocksResponse.DayRange;
import com.naleul.naleul.domain.lifePattern.service.FixedBlockResolver;
import com.naleul.naleul.domain.lifePattern.service.LifePatternService;
import com.naleul.naleul.domain.lifePattern.support.DayFixedBlocks;
import com.naleul.naleul.domain.lifePattern.support.FixedBlock;
import com.naleul.naleul.domain.milestone.entity.Milestone;
import com.naleul.naleul.domain.pattern.entity.TaskScheduleLog;
import com.naleul.naleul.domain.pattern.enums.TaskScheduleAction;
import com.naleul.naleul.domain.pattern.repository.TaskScheduleLogRepository;
import com.naleul.naleul.domain.planner.dto.response.MonthlyTimetableResponse;
import com.naleul.naleul.domain.planner.dto.response.TimeBlockTaskResponse;
import com.naleul.naleul.domain.planner.dto.response.TimetableResponse;
import com.naleul.naleul.domain.planner.entity.DailyPlan;
import com.naleul.naleul.domain.planner.repository.DailyPlanRepository;
import com.naleul.naleul.domain.planner.repository.TimetableQueryRepository;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * TimeTable 조회 (명세 3 · 5-2). 일간·주간·월간.
 *
 * 일간/주간 Task 소속 규칙
 *  - 블록 위치: 완료 전에는 계획 시각(plannedStartAt~plannedEndAt),
 *              완료 후 실제 수행 시각이 있으면 실제 시각(task_actual 또는 미션의 task.actual_*).
 *              → 계획과 다른 날에 완료하면 실제로 한 날 화면으로 옮겨 간다.
 *  - 블록 구간이 그날 00:00~24:00 과 겹치면 그날 tasks 에 포함 (자정 넘김 Task 는 이틀 모두에 나옴, 화면에서 잘라 그림)
 *  - 블록 구간이 없는 Task(시간 미정): routineDate 가 그날이면 unscheduledTasks
 *  - 통계(stats)는 "계획한 날"의 "시작한 날" 기준으로만 센다 (실행률은 계획 대비이므로 실제 시각으로 옮기지 않음)
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TimetableQueryService {

    private static final int MAX_CHIPS = 3;

    private final TimetableQueryRepository queryRepository;
    private final FixedBlockResolver fixedBlockResolver;
    private final LifePatternService lifePatternService;
    private final DailyPlanRepository dailyPlanRepository;
    private final PlannerClock clock;
    /** 22시 이월 기록 — 이월된 Task 를 "원래 날"의 실천률에 계속 남기기 위해 읽는다 */
    private final TaskScheduleLogRepository taskScheduleLogRepository;
    private final ActualRangeReader actualRangeReader;
    /** 시간대를 정한 습관 (V56) */
    private final HabitService habitService;
    /** 자동 배치가 나눠 넣은 계획 구간 (V65). 단위 테스트처럼 없으면 구간 없이 응답 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PlannedSegmentStore plannedSegmentStore;

    // ── 일간 · 주간 ──────────────────────────────────────

    public TimetableResponse getTimetable(Long userId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)
                || ChronoUnit.DAYS.between(startDate, endDate) > 6) {
            throw new CustomException(ErrorCode.TIMETABLE_INVALID_RANGE);
        }
        lifePatternService.ensureInitialized(userId);
        LocalDateTime now = clock.now();

        // 전날도 같이 계산: 전날 하루가 자정을 넘겨 끝나면(예: 취침 01:00) 이날 00:00~01:00 은 전날 칸에 그린다
        Map<LocalDate, DayFixedBlocks> fixed = fixedBlockResolver.resolve(userId, startDate.minusDays(1), endDate);
        LocalDateTime from = startDate.atStartOfDay();
        // 마지막 날이 자정을 넘겨 끝나면 그 시간의 Task 까지 불러온다
        LocalDateTime to = later(endDate.plusDays(1).atStartOfDay(), fixed.get(endDate).dayRange().end());
        List<Task> placed = queryRepository.findPlacedInRange(userId, from, to);
        List<Task> unscheduled = queryRepository.findUnscheduledInRange(userId, startDate, endDate);
        // 계획은 기간 밖인데 실제로는 기간 안에 완료한 Task
        List<Task> doneInRange = queryRepository.findCompletedWithActualInRange(userId, from, to);
        Map<LocalDate, DailyPlan> plans = new HashMap<>();
        dailyPlanRepository.findAllInRange(userId, startDate, endDate).forEach(p -> plans.put(p.getPlanDate(), p));
        Map<LocalDate, Set<Long>> carriedOut = carriedOutByDate(userId, startDate, endDate);
        Map<LocalDate, List<TaskScheduleLog>> carriedLogs = carriedLogsByDate(userId, startDate, endDate);
        Map<Long, Task> carriedTasks = new HashMap<>();
        Set<Long> carriedIds = new HashSet<>();
        carriedLogs.values().forEach(ls -> ls.forEach(l -> carriedIds.add(l.getTaskId())));
        if (!carriedIds.isEmpty()) {
            for (Task t : queryRepository.findAllOfUserByIds(userId, carriedIds)) carriedTasks.put(t.getTaskId(), t);
        }
        Map<LocalDate, List<TimetableResponse.HabitBlock>> habitBlocks = new HashMap<>();
        if (habitService != null) {
            for (HabitService.HabitSlot h : habitService.timedSlots(userId, startDate, endDate)) {
                habitBlocks.computeIfAbsent(h.date(), k -> new ArrayList<>()).add(
                        new TimetableResponse.HabitBlock(h.habitId(), h.name(), h.emoji(), h.start(), h.end(), h.checked()));
            }
        }

        Map<Long, Task> candidates = new LinkedHashMap<>();
        for (Task t : concat(concat(placed, unscheduled), doneInRange)) candidates.putIfAbsent(t.getTaskId(), t);
        Map<Long, DateTimeRange> actuals = actualRanges(candidates.values());
        Map<Long, List<DateTimeRange>> segments = actualRangeReader != null
                ? actualRangeReader.actualSegments(candidates.values()) : Map.of();
        // 자동 배치가 나눠 넣은 계획 구간 (V65)
        Map<Long, List<DateTimeRange>> plannedSegs = plannedSegmentStore != null
                ? plannedSegmentStore.segmentsOf(candidates.values()) : Map.of();
        Map<Long, DateTimeRange> blocks = new HashMap<>();
        for (Task t : candidates.values()) {
            DateTimeRange r = blockRange(t, actuals.get(t.getTaskId()));
            if (r != null) blocks.put(t.getTaskId(), r);
        }

        List<TimetableResponse.Day> days = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            // 이 날 칸에 그릴 구간: [그날 0시 (전날 하루가 넘어왔으면 그 끝), max(다음 날 0시, 그날 취침)]
            //  예) 취침 01:00 → 10/6 칸은 10/6 00:00(또는 전날 취침 이후) ~ 10/7 01:00, 10/7 칸은 10/7 01:00 부터
            DayFixedBlocks prev = fixed.get(d.minusDays(1));
            LocalDateTime dayStart = later(d.atStartOfDay(), prev != null ? prev.dayRange().end() : null);
            LocalDateTime dayEnd = later(d.plusDays(1).atStartOfDay(), fixed.get(d).dayRange().end());
            final LocalDate date = d;

            List<Task> dayPlaced = candidates.values().stream()
                    .filter(t -> {
                        DateTimeRange r = blocks.get(t.getTaskId());
                        return r != null && r.start().isBefore(dayEnd) && r.end().isAfter(dayStart);
                    })
                    .sorted(Comparator.comparing((Task t) -> blocks.get(t.getTaskId()).start()))
                    .toList();
            List<Task> dayUnscheduled = candidates.values().stream()
                    .filter(t -> !blocks.containsKey(t.getTaskId()) && date.equals(t.getRoutineDate()))
                    .toList();

            DayFixedBlocks f = fixed.get(d);
            List<TimetableResponse.FixedBlock> fixedBlocks = f.displayBlocks().stream()
                    .map(b -> toFixedBlock(b, dayPlaced, blocks))
                    .toList();

            // 통계는 지금까지처럼 계획 기준 (계획 시작일 = 그날, 시간 미정은 routineDate = 그날)
            List<Task> statPlaced = placed.stream()
                    .filter(t -> t.getPlannedStartAt().toLocalDate().equals(date))
                    .toList();
            List<Task> statUnscheduled = unscheduled.stream()
                    .filter(t -> date.equals(t.getRoutineDate()))
                    .toList();
            int carried = carriedCount(carriedOut.get(d), statPlaced, statUnscheduled);
            List<TimetableResponse.CarriedOutBlock> traces = carriedTraces(carriedLogs.get(d), carriedTasks,
                    dayPlaced, dayUnscheduled);

            days.add(new TimetableResponse.Day(
                    d,
                    new DayRange(f.dayRange().start(), f.dayRange().end()),
                    fixedBlocks,
                    dayPlaced.stream().map(t -> response(t, now, actuals, segments, plannedSegs)).toList(),
                    dayUnscheduled.stream().map(t -> response(t, now, actuals, segments, plannedSegs)).toList(),
                    toPlan(plans.get(d)),
                    stats(statPlaced, statUnscheduled, now, carried),
                    new TimetableResponse.Sleep(
                            f.wakeSleep() != null ? toFixedBlock(f.wakeSleep(), dayPlaced, blocks) : null,
                            f.bedSleep() != null ? toFixedBlock(f.bedSleep(), dayPlaced, blocks) : null),
                    habitBlocks.getOrDefault(d, List.of()),
                    traces));
        }
        return new TimetableResponse(days);
    }

    /** 사용자가 시간을 정한 Task(잠김)나 완료한 Task 가 고정 블록을 덮는 구간 (완료한 Task 는 실제 시각 기준) */
    private TimetableResponse.FixedBlock toFixedBlock(FixedBlock b, List<Task> dayPlaced, Map<Long, DateTimeRange> blocks) {
        List<TimetableResponse.Covered> covered = new ArrayList<>();
        for (Task t : dayPlaced) {
            if (!t.isScheduleLocked() && t.getTaskStatus() != TaskStatus.COMPLETED) continue;
            DateTimeRange r = blocks.get(t.getTaskId());
            LocalDateTime s = r.start();
            LocalDateTime e = r.end();
            if (s.isBefore(b.end()) && e.isAfter(b.start())) {
                covered.add(new TimetableResponse.Covered(t.getTaskId(),
                        s.isAfter(b.start()) ? s : b.start(),
                        e.isBefore(b.end()) ? e : b.end()));
            }
        }
        return new TimetableResponse.FixedBlock(b.lifePatternId(), b.patternType(), b.title(), b.emoji(),
                b.targetDate(), b.start(), b.end(), b.overridden(), b.clippedFromPreviousDay(), covered);
    }

    // ── 블록 위치: 계획 시각 vs 실제 시각 ──────────────────────

    /**
     * 완료한 Task 의 실제 수행 구간.
     * task_actual(일반·루틴, "계획과 다른 시간에 했어요")이 있으면 그걸, 없으면 Task 자체 컬럼(미션)을 쓴다.
     * 실제 시각 없이 완료만 누른 Task 는 "계획한 시각에 했다"고 보고 넣지 않는다.
     */
    public Map<Long, DateTimeRange> actualRanges(Collection<Task> tasks) {
        return actualRangeReader.actualRanges(tasks);
    }

    /** 화면에 그릴 구간: 완료 + 실제 시각 있음 → 실제, 아니면 계획. 둘 다 없으면 null (시간 미정) */
    public static DateTimeRange blockRange(Task t, DateTimeRange actual) {
        if (t.getTaskStatus() == TaskStatus.COMPLETED && actual != null) return actual;
        if (t.getPlannedStartAt() == null) return null;
        return new DateTimeRange(t.getPlannedStartAt(), endOf(t));
    }

    private static TimeBlockTaskResponse response(Task t, LocalDateTime now, Map<Long, DateTimeRange> actuals,
                                                  Map<Long, List<DateTimeRange>> segments,
                                                  Map<Long, List<DateTimeRange>> plannedSegs) {
        return TimeBlockTaskResponse.from(t, now, actuals.get(t.getTaskId()),
                segments.getOrDefault(t.getTaskId(), List.of()), plannedSegs.getOrDefault(t.getTaskId(), List.of()));
    }

    private static TimetableResponse.Plan toPlan(DailyPlan p) {
        if (p == null) return null;
        return new TimetableResponse.Plan(p.getStatus(), p.getSource(), p.getSummary(), p.needsReview(), p.getGeneratedAt());
    }

    static TimetableResponse.Stats stats(List<Task> placedStartingToday, List<Task> unscheduled, LocalDateTime now) {
        return stats(placedStartingToday, unscheduled, now, 0);
    }

    /**
     * @param carriedOver 이날 못 해서 다음 날로 이월된 Task 수.
     *   이월되면 Task 의 날짜가 다음 날로 바뀌어 이날 목록에서 사라진다. 그대로 두면 분모가 줄어
     *   어제 60% 였던 실천률이 다음 날 아침 65% 로 바뀌므로, "이날 계획했는데 못 한 일"로 다시 넣는다.
     */
    static TimetableResponse.Stats stats(List<Task> placedStartingToday, List<Task> unscheduled, LocalDateTime now,
                                         int carriedOver) {
        int total = carriedOver, completed = 0, missed = carriedOver;
        for (Task t : concat(placedStartingToday, unscheduled)) {
            if (t.getTaskStatus() == TaskStatus.SKIPPED) continue; // 건너뛴 일은 분모에서 뺀다
            total++;
            if (t.getTaskStatus() == TaskStatus.COMPLETED) completed++;
            else if (TimeBlockTaskResponse.from(t, now).missed()) missed++;
        }
        Integer rate = total == 0 ? null : (int) Math.round(completed * 100.0 / total);
        return new TimetableResponse.Stats(total, completed, missed, rate, carriedOver);
    }

    /** 원래 날짜별 이월된 Task id (22시 이월 기록 기준). 기록 저장소가 없으면(단위 테스트) 빈 맵 */
    private Map<LocalDate, Set<Long>> carriedOutByDate(Long userId, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, Set<Long>> map = new HashMap<>();
        if (taskScheduleLogRepository == null) return map;
        for (TaskScheduleLog l : taskScheduleLogRepository.findAllByActionAndFromDateBetween(
                userId, TaskScheduleAction.CARRY_OVER, startDate, endDate)) {
            if (l.getFromDate() == null || l.getFromDate().equals(l.getToDate())) continue;
            map.computeIfAbsent(l.getFromDate(), k -> new HashSet<>()).add(l.getTaskId());
        }
        return map;
    }

    /** 이월 기록 (원래 날짜별, 같은 Task 가 여러 번 이월됐으면 그날 기준 마지막 기록) */
    private Map<LocalDate, List<TaskScheduleLog>> carriedLogsByDate(Long userId, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, Map<Long, TaskScheduleLog>> map = new HashMap<>();
        if (taskScheduleLogRepository == null) return Map.of();
        for (TaskScheduleLog l : taskScheduleLogRepository.findAllByActionAndFromDateBetween(
                userId, TaskScheduleAction.CARRY_OVER, startDate, endDate)) {
            if (l.getFromDate() == null || l.getFromDate().equals(l.getToDate())) continue;
            map.computeIfAbsent(l.getFromDate(), k -> new LinkedHashMap<>()).put(l.getTaskId(), l);
        }
        Map<LocalDate, List<TaskScheduleLog>> out = new HashMap<>();
        map.forEach((d, m) -> out.put(d, new ArrayList<>(m.values())));
        return out;
    }

    /**
     * 이월 흔적 블록 (2026-10). 원래 계획 시각에 점선으로 남겨 "오늘 못 했다"는 걸 보여 주고, 지금 있는 날을 알려 준다.
     * 이미 그날 목록에 다시 있거나(되돌림), Task 가 지워졌으면 넣지 않는다.
     */
    static List<TimetableResponse.CarriedOutBlock> carriedTraces(List<TaskScheduleLog> logs, Map<Long, Task> tasks,
                                                                 List<Task> placed, List<Task> unscheduled) {
        if (logs == null || logs.isEmpty()) return List.of();
        Set<Long> present = new HashSet<>();
        for (Task t : concat(placed, unscheduled)) present.add(t.getTaskId());
        List<TimetableResponse.CarriedOutBlock> out = new ArrayList<>();
        for (TaskScheduleLog l : logs) {
            if (present.contains(l.getTaskId())) continue;
            Task t = tasks.get(l.getTaskId());
            if (t == null || t.getTaskStatus() == TaskStatus.DELETED) continue;
            LocalDateTime start = l.getFromStartAt();
            Long minutes = t.getPlannedDurationMinutes();
            LocalDateTime end = start == null ? null : start.plusMinutes(minutes != null && minutes > 0 ? minutes : 30);
            LocalDate now = t.getPlannedStartAt() != null ? t.getPlannedStartAt().toLocalDate()
                    : t.getRoutineDate() != null ? t.getRoutineDate() : l.getToDate();
            // 그날보다 앞으로 되돌려 놓은 Task 의 흔적은 "→ 이전 날로 옮김"처럼 헷갈려서 보이지 않아요 (2026-10)
            if (now != null && l.getFromDate() != null && !now.isAfter(l.getFromDate())) continue;
            out.add(new TimetableResponse.CarriedOutBlock(t.getTaskId(), t.getTaskName(), t.getEmoji(),
                    colorOf(t.getGoalCategory()), start, end, now));
        }
        out.sort(Comparator.comparing(TimetableResponse.CarriedOutBlock::start, Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    /**
     * 이월 기록 중 지금도 그날 목록에 없는 Task 수.
     * (이월 후 사용자가 다시 그날로 옮겨 놓았다면 이미 목록에서 세므로 두 번 세지 않는다)
     */
    static int carriedCount(Set<Long> carriedIds, List<Task> placed, List<Task> unscheduled) {
        if (carriedIds == null || carriedIds.isEmpty()) return 0;
        Set<Long> present = new HashSet<>();
        for (Task t : concat(placed, unscheduled)) present.add(t.getTaskId());
        int n = 0;
        for (Long id : carriedIds) if (!present.contains(id)) n++;
        return n;
    }

    // ── 월간 ─────────────────────────────────────────────

    public MonthlyTimetableResponse getMonthly(Long userId, int year, int month) {
        if (month < 1 || month > 12 || year < 2000 || year > 2100) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        YearMonth ym = YearMonth.of(year, month);
        LocalDate first = ym.atDay(1);
        LocalDate last = ym.atEndOfMonth();

        List<Task> placed = queryRepository.findPlacedInRange(userId, first.atStartOfDay(), last.plusDays(1).atStartOfDay());
        List<Task> unscheduled = queryRepository.findUnscheduledInRange(userId, first, last);
        List<Milestone> milestones = queryRepository.findMilestonesInRange(userId, first, last);

        Map<LocalDate, List<Task>> byDate = new TreeMap<>();
        for (Task t : placed) {
            LocalDate d = t.getPlannedStartAt().toLocalDate();
            if (!d.isBefore(first) && !d.isAfter(last)) byDate.computeIfAbsent(d, k -> new ArrayList<>()).add(t);
        }
        for (Task t : unscheduled) byDate.computeIfAbsent(t.getRoutineDate(), k -> new ArrayList<>()).add(t);
        Map<LocalDate, Set<Long>> carriedOut = carriedOutByDate(userId, first, last);
        Map<LocalDate, List<Milestone>> msByDate = new HashMap<>();
        for (Milestone m : milestones) msByDate.computeIfAbsent(m.getDueDate(), k -> new ArrayList<>()).add(m);

        List<MonthlyTimetableResponse.Day> days = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            List<Task> tasks = byDate.getOrDefault(d, List.of());
            // 이월돼 이날 목록에서 빠진 Task 도 "이날 못 한 일"로 센다 (실천률 유지)
            int total = carriedCount(carriedOut.get(d), tasks, List.of()), done = 0;
            for (Task t : tasks) {
                if (t.getTaskStatus() == TaskStatus.SKIPPED) continue;
                total++;
                if (t.getTaskStatus() == TaskStatus.COMPLETED) done++;
            }
            List<Task> nonRoutine = tasks.stream()
                    .filter(t -> t.getRoutine() == null)
                    .sorted(Comparator.comparing((Task t) -> t.getPlannedStartAt() == null)
                            .thenComparing(t -> t.getPlannedStartAt(), Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            List<MonthlyTimetableResponse.Chip> chips = nonRoutine.stream().limit(MAX_CHIPS)
                    .map(t -> new MonthlyTimetableResponse.Chip(t.getTaskId(), t.getTaskName(), colorOf(t.getGoalCategory()),
                            t.getTaskStatus() == TaskStatus.COMPLETED,
                            t.getGoalCategory() != null && t.getGoalCategory().isTemporary()))
                    .toList();
            List<MonthlyTimetableResponse.MilestoneChip> ms = msByDate.getOrDefault(d, List.of()).stream()
                    .map(m -> new MonthlyTimetableResponse.MilestoneChip(m.getMilestoneId(), m.getTitle(),
                            colorOf(m.getGoalCategory()), m.getGoalCategory().getGoalCategoryId()))
                    .toList();
            days.add(new MonthlyTimetableResponse.Day(d, total == 0 ? null : (int) Math.round(done * 100.0 / total),
                    total, done, chips, Math.max(nonRoutine.size() - chips.size(), 0), ms));
        }
        return new MonthlyTimetableResponse(year, month, days, deadlinesThisWeek(userId));
    }

    /** 이번 주(월~일) 마감인 미완료 Task + 마일스톤 — 월간 왼쪽 "이번 주 마감" */
    private List<MonthlyTimetableResponse.Deadline> deadlinesThisWeek(Long userId) {
        LocalDate today = clock.today();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        List<MonthlyTimetableResponse.Deadline> list = new ArrayList<>();
        for (Task t : queryRepository.findDueInRange(userId, monday, sunday)) {
            list.add(new MonthlyTimetableResponse.Deadline("TASK", t.getTaskId(), t.getTaskName(), t.getDueDate(),
                    colorOf(t.getGoalCategory())));
        }
        for (Milestone m : queryRepository.findMilestonesInRange(userId, monday, sunday)) {
            list.add(new MonthlyTimetableResponse.Deadline("MILESTONE", m.getMilestoneId(), m.getTitle(), m.getDueDate(),
                    colorOf(m.getGoalCategory())));
        }
        list.sort(Comparator.comparing(MonthlyTimetableResponse.Deadline::date));
        return list;
    }

    // ── 유틸 ─────────────────────────────────────────────

    private static String colorOf(GoalCategory g) {
        return g != null && g.getUserColor() != null ? g.getUserColor().getColorCode() : null;
    }

    private static LocalDateTime endOf(Task t) {
        if (t.getPlannedEndAt() != null && t.getPlannedEndAt().isAfter(t.getPlannedStartAt())) return t.getPlannedEndAt();
        return t.getPlannedStartAt().plusMinutes(t.durationMinutesOr(30));
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> r = new ArrayList<>(a);
        r.addAll(b);
        return r;
    }

    /** 둘 중 늦은 시각 (b 가 null 이면 a) */
    private static LocalDateTime later(LocalDateTime a, LocalDateTime b) {
        return b != null && b.isAfter(a) ? b : a;
    }
}
