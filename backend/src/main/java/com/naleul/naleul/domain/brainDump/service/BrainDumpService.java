package com.naleul.naleul.domain.brainDump.service;

import com.naleul.naleul.domain.work.entity.WorkTopic;
import com.naleul.naleul.domain.work.repository.WorkTopicRepository;
import com.naleul.naleul.domain.aiQuality.service.AiSuggestionLogWriter;
import com.naleul.naleul.domain.aiQuality.service.BrainDumpFingerprint;
import com.naleul.naleul.domain.aiUsage.enums.AiFeature;
import com.naleul.naleul.domain.aiUsage.quota.AiQuotaService;
import com.naleul.naleul.domain.brainDump.client.BrainDumpAiClient;
import com.naleul.naleul.domain.brainDump.client.BrainDumpAiRequest;
import com.naleul.naleul.domain.brainDump.client.BrainDumpAiResult;
import com.naleul.naleul.domain.brainDump.dto.request.BrainDumpConfirmRequest;
import com.naleul.naleul.domain.brainDump.dto.request.BrainDumpParseRequest;
import com.naleul.naleul.domain.brainDump.dto.response.BrainDumpConfirmResponse;
import com.naleul.naleul.domain.brainDump.dto.response.BrainDumpParseResponse;
import com.naleul.naleul.domain.brainDump.enums.DateType;
import com.naleul.naleul.domain.brainDump.enums.GoalMatchType;
import com.naleul.naleul.domain.brainDump.support.BrainDumpResultSanitizer;
import com.naleul.naleul.domain.brainDump.support.BrainDumpRules;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.goalCreation.service.GoalColorAssigner;
import com.naleul.naleul.domain.milestone.entity.Milestone;
import com.naleul.naleul.domain.pattern.service.TaskScheduleLogWriter;
import com.naleul.naleul.domain.planner.repository.PlacementQueryRepository;
import com.naleul.naleul.domain.planner.service.ActualRangeReader;
import com.naleul.naleul.domain.planner.service.DailyPlanService;
import com.naleul.naleul.domain.planner.service.DayPlacementService;
import com.naleul.naleul.domain.planner.service.PlacementMode;
import com.naleul.naleul.domain.planner.service.PlacementOutcome;
import com.naleul.naleul.domain.planner.service.PlannerSettingService;
import com.naleul.naleul.domain.planner.support.Concurrency;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.*;
import com.naleul.naleul.domain.task.policy.TaskQuotaValidator;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Brain dump (명세 3 · 3-2, 5-4).
 *
 * parse  : 저장 없음. AI 로 해석 → 서버 규칙으로 다듬어 2단계 화면 데이터. AI 가 실패하면 규칙 해석(aiFailed=true)
 * confirm: 한 트랜잭션.
 *   1) 사용자가 정한 시각이 다른 잠긴 일정과 겹치면 → 아무것도 저장하지 않고 conflicts 반환
 *   2) Free 한도 (오늘 만든 개수 + 이번 개수 ≤ 2)
 *   3) 임시 목표 생성(최대 5개 보유) → 목표별 "기타 할 일" 확보 → Task 저장
 *   4) 날짜별로 바로 배치 (빈 시간이 없으면 마감일 안의 다음 날, 그래도 없으면 "시간 미정")
 *   5) 배치된 날짜를 daily_plan 에 BRAIN_DUMP 로 기록 (3단계에서 이미 확인했으므로 REVIEWED)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrainDumpService {

    static final int MAX_TEMP_GOALS_OWNED = 5;

    private final BrainDumpAiClient aiClient;
    private final AiQuotaService aiQuotaService;
    private final BrainDumpGoalContextLoader contextLoader;
    private final UserFinder userFinder;
    private final GoalCategoryRepository goalCategoryRepository;
    private final GeneralCategoryRepository generalCategoryRepository;
    private final GoalColorAssigner colorAssigner;
    private final TaskRepository taskRepository;
    private final TaskQuotaValidator taskQuotaValidator;
    private final PlacementQueryRepository placementQueryRepository;
    private final DayPlacementService dayPlacementService;
    private final PlannerSettingService plannerSettingService;
    private final DailyPlanService dailyPlanService;
    private final PlannerClock clock;
    /** 나의 패턴: Brain dump 확정 기록 (주 n회, 주로 추가하는 때, 명세 4 · 2-8) */
    private final TaskScheduleLogWriter scheduleLogWriter;
    /** 업무형 목표: 2단계에서 고른 주제 (2026-10) */
    private final WorkTopicRepository workTopicRepository;
    /** 완료한 Task 의 실제 시각 (없으면 계획 시각으로 판정) */
    @Autowired(required = false)
    private ActualRangeReader actualRangeReader;
    /** AI 품질 지표: 해석 결과 → 확정 때 얼마나 고쳤는지 (V64) */
    @Autowired(required = false)
    private AiSuggestionLogWriter suggestionLog;

    // ── 1단계 → 2단계 ─────────────────────────────────────

    /** 트랜잭션 없음: AI 호출(최대 15초) 동안 DB 커넥션을 잡고 있지 않도록 */
    public BrainDumpParseResponse parse(Long userId, BrainDumpParseRequest request) {
        LocalDate today = clock.today();
        BrainDumpGoalContextLoader.Context ctx = contextLoader.load(userId);

        // 오늘 AI 해석 횟수를 다 썼으면 AI 없이 규칙 해석으로 계속 (막지 않는다 — 브레인 덤프 자체는 무료로 계속 쓸 수 있게)
        Long usageId = aiQuotaService.tryConsume(userId, AiFeature.BRAIN_DUMP);
        if (usageId == null) {
            return logSuggested(userId,
                    BrainDumpResultSanitizer.sanitize(request, null, ctx.goals(), ctx.colors(), today).limited());
        }

        BrainDumpAiResult ai = null;
        try {
            ai = aiClient.parse(new BrainDumpAiRequest(today,
                    request.items().stream().map(i -> new BrainDumpAiRequest.Item(i.clientKey(), i.text())).toList(),
                    ctx.goals()));
        } catch (RuntimeException e) {
            // AI 가 안 돼도 사용자는 계속 진행할 수 있어야 한다 → 규칙 해석으로 대체 (횟수는 돌려준다)
            log.warn("[BrainDump] AI 해석 실패, 규칙 해석으로 대체 userId={} : {}", userId, e.getMessage());
            aiQuotaService.refund(usageId);
        }
        return logSuggested(userId, BrainDumpResultSanitizer.sanitize(request, ai, ctx.goals(), ctx.colors(), today));
    }

    /** AI 품질 지표: 해석 결과 지문을 남긴다 (제목은 해시만). AI 를 못 쓴 규칙 해석도 남기되 aiUsed=false */
    private BrainDumpParseResponse logSuggested(Long userId, BrainDumpParseResponse res) {
        if (suggestionLog != null) {
            int linked = (int) res.items().stream()
                    .filter(i -> i.goal() != null && i.goal().type() != GoalMatchType.NONE).count();
            suggestionLog.suggested(userId, AiFeature.BRAIN_DUMP, null, !res.aiFailed(),
                    res.items().size(), linked, BrainDumpFingerprint.of(res));
        }
        return res;
    }

    // ── 2단계 → 3단계 ─────────────────────────────────────

    @Transactional
    public BrainDumpConfirmResponse confirm(Long userId, BrainDumpConfirmRequest request) {
        User user = userFinder.findUser(userId);
        LocalDate today = clock.today();
        List<BrainDumpConfirmRequest.Item> items = request.items();
        Map<String, BrainDumpConfirmRequest.TempGoal> tempInputs = validateLinks(items, request.tempGoals());

        plannerSettingService.getOrCreateForUpdate(userId); // 같은 사용자의 배치 작업과 순서 맞추기

        // 1) 사용자가 정한 시각 검사
        Map<String, DateTimeRange> explicit = new LinkedHashMap<>();
        for (BrainDumpConfirmRequest.Item i : items) {
            if (i.startTime() == null) continue;
            LocalDate date = clamp(i.scheduledDate() != null ? i.scheduledDate() : i.dueDate(), today);
            explicit.put(i.clientKey(), BrainDumpRules.explicitRange(date, i.startTime(), i.endTime(), minutesOf(i)));
        }
        List<Task> pushed = new ArrayList<>();
        List<BrainDumpConfirmResponse.Conflict> conflicts = findConflicts(userId, items, explicit, pushed);
        if (!conflicts.isEmpty()) {
            return BrainDumpConfirmResponse.conflicted(conflicts);
        }

        // 2) Free 한도
        taskQuotaValidator.validateCanCreate(user, items.size(), null);

        // 3) 임시 목표 → 목표 · 세부 목표 · 마일스톤 결정 → Task 저장
        Map<String, GoalCategory> tempGoals = createTempGoals(user, items, tempInputs, today);
        Map<Long, GeneralCategory> bucketCache = new HashMap<>();
        Map<Long, Task> created = new LinkedHashMap<>();
        Map<Long, String> clientKeyOf = new HashMap<>();
        Map<LocalDate, Set<Long>> toPlace = new TreeMap<>();
        boolean premium = user.isCurrentlyPremium();

        for (BrainDumpConfirmRequest.Item i : items) {
            GoalCategory goal = i.tempGoalKey() != null ? tempGoals.get(i.tempGoalKey()) : findGoal(user, i.goalCategoryId());
            GeneralCategory general = resolveGeneral(user, goal, i.generalCategoryId(), bucketCache);
            Milestone milestone = resolveMilestone(goal, i.milestoneId());
            WorkTopic workTopic = resolveWorkTopic(goal, i.workTopicId());

            DateTimeRange range = explicit.get(i.clientKey());
            DateType type = range != null ? DateType.ON : (i.dateType() == DateType.DUE && i.dueDate() != null ? DateType.DUE : DateType.ON);
            LocalDate scheduled = type == DateType.ON ? clamp(range != null ? range.start().toLocalDate() : i.scheduledDate(), today) : null;
            LocalDate due = i.dueDate() != null ? clamp(i.dueDate(), today) : null;
            LocalDate firstTry = range != null ? range.start().toLocalDate()
                    : BrainDumpRules.firstTryDate(type, scheduled, due, user.getPlanningStyle(), today);

            Task task = Task.builder()
                    .taskName(i.title().strip())
                    .taskPriority(i.priority() != null ? i.priority() : TaskPriority.C)
                    .taskStatus(TaskStatus.TODO)
                    .user(user)
                    .goalCategory(goal)
                    .generalCategory(general)
                    .milestone(milestone)
                    .workTopic(workTopic)
                    .scheduledDate(scheduled)
                    .dueDate(due)
                    .routineDate(firstTry)
                    .plannedStartAt(range != null ? range.start() : null)
                    .plannedEndAt(range != null ? range.end() : null)
                    .plannedDurationMinutes((long) (range != null ? range.minutes() : minutesOf(i)))
                    .taskKind(i.kind() != null ? i.kind() : TaskKind.NORMAL)
                    .source(TaskSource.USER_INPUT)
                    .notificationEnabled(premium && range != null) // 시작 알림은 Pro + 시각이 있는 일정만
                    .defaultSettingStatus(false)
                    .build();
            taskRepository.save(task); // @PrePersist: 시각이 있으면 잠금(USER), created_at 기록
            created.put(task.getTaskId(), task);
            clientKeyOf.put(task.getTaskId(), i.clientKey());
            if (range == null) toPlace.computeIfAbsent(firstTry, k -> new LinkedHashSet<>()).add(task.getTaskId());
        }

        // 4) 밀려난 자동 배치 블록 + 새 Task 배치
        Map<Long, Task> pushedById = new LinkedHashMap<>();
        for (Task p : pushed) {
            LocalDate d = p.getPlannedStartAt().toLocalDate();
            p.unplace();
            pushedById.put(p.getTaskId(), p);
            toPlace.computeIfAbsent(d, k -> new LinkedHashSet<>()).add(p.getTaskId());
        }

        List<BrainDumpConfirmResponse.Placement> placements = new ArrayList<>();
        List<BrainDumpConfirmResponse.Placement> moved = new ArrayList<>();
        List<BrainDumpConfirmResponse.Unscheduled> unscheduled = new ArrayList<>();
        // 시간을 정하지 않은 새 Task 는 바로 놓지 않고 미리보기로 넘긴다 (화면에서 "이대로 진행할까요?")
        List<BrainDumpConfirmResponse.Pending> pending = new ArrayList<>();
        if (request.placeLaterOrFalse()) {
            for (Task t : created.values()) {
                if (t.isPlaced()) continue;
                pending.add(new BrainDumpConfirmResponse.Pending(t.getTaskId(), clientKeyOf.get(t.getTaskId()),
                        t.getTaskName(), t.getRoutineDate()));
                Set<Long> ids = toPlace.get(t.getRoutineDate());
                if (ids != null) ids.remove(t.getTaskId());
            }
            toPlace.values().removeIf(Set::isEmpty);
        }

        for (Task t : created.values()) {
            if (t.isPlaced()) {
                placements.add(new BrainDumpConfirmResponse.Placement(t.getTaskId(), clientKeyOf.get(t.getTaskId()),
                        t.getTaskName(), t.getPlannedStartAt().toLocalDate(), t.getPlannedStartAt(), t.getPlannedEndAt(), null, true));
            }
        }
        toPlace.forEach((date, ids) -> {
            PlacementOutcome out = dayPlacementService.placeDay(userId, date, PlacementMode.FILL, ids, true);
            out.placed().forEach(b -> placements.add(toPlacement(b, created, pushedById, clientKeyOf)));
            out.movedToOtherDays().forEach(b -> moved.add(toPlacement(b, created, pushedById, clientKeyOf)));
            for (Long id : out.unscheduled()) {
                Task t = created.containsKey(id) ? created.get(id) : pushedById.get(id);
                if (t != null) unscheduled.add(new BrainDumpConfirmResponse.Unscheduled(id, clientKeyOf.get(id), t.getTaskName(), t.getRoutineDate()));
            }
        });
        placements.sort(Comparator.comparing(BrainDumpConfirmResponse.Placement::start));

        // 배치가 일어난 날짜를 일일 계획에 기록 (이미 22시 계획이 있는 날짜는 그대로 둔다)
        Set<LocalDate> touched = new TreeSet<>();
        placements.forEach(p -> touched.add(p.date()));
        moved.forEach(p -> touched.add(p.date()));
        pending.forEach(p -> { if (p.date() != null) touched.add(p.date()); });
        dailyPlanService.recordBrainDump(userId, touched, placements.size() + moved.size() + pending.size());

        List<BrainDumpConfirmResponse.CreatedTempGoal> createdTemps = new ArrayList<>();
        tempGoals.forEach((key, g) -> createdTemps.add(new BrainDumpConfirmResponse.CreatedTempGoal(key, g.getGoalCategoryId(),
                g.getGoalCategoryName(), g.getEmoji(), g.getUserColor() != null ? g.getUserColor().getColorCode() : null)));

        scheduleLogWriter.brainDumpConfirmed(userId, created.size(), createdTemps.size());
        if (suggestionLog != null) {
            // AI 해석 결과와 비교해 고친 항목 수 (짝지을 해석이 없으면 기록하지 않는다)
            suggestionLog.latestPending(userId, AiFeature.BRAIN_DUMP, null).ifPresent(p -> {
                BrainDumpFingerprint.Diff diff = BrainDumpFingerprint.diff(p.getSnapshotJson(), items);
                suggestionLog.applied(userId, AiFeature.BRAIN_DUMP, null, created.size(), null,
                        diff.edited(), diff.goalChanged(), created.keySet(), List.of());
            });
        }

        log.info("[BrainDump] 확정 userId={} tasks={} placed={} moved={} unscheduled={} pending={} tempGoals={}",
                userId, created.size(), placements.size(), moved.size(), unscheduled.size(), pending.size(), createdTemps.size());
        return new BrainDumpConfirmResponse(true, placements, moved, unscheduled, List.of(), createdTemps, pending);
    }

    // ── 검증 · 조회 ──────────────────────────────────────

    /** 모든 Task 가 목표(기존 id 또는 임시 목표 key) 하나에 연결됐는지 */
    /** 업무형 목표의 주제 — 그 목표의 지워지지 않은 주제만 (아니면 주제 없음) */
    private WorkTopic resolveWorkTopic(GoalCategory goal, Long workTopicId) {
        if (goal == null || workTopicId == null || goal.getGoalCategoryId() == null) return null;
        return workTopicRepository.findInGoal(workTopicId, goal.getGoalCategoryId()).orElse(null);
    }

    private Map<String, BrainDumpConfirmRequest.TempGoal> validateLinks(List<BrainDumpConfirmRequest.Item> items,
                                                                       List<BrainDumpConfirmRequest.TempGoal> temps) {
        Map<String, BrainDumpConfirmRequest.TempGoal> byKey = new LinkedHashMap<>();
        for (BrainDumpConfirmRequest.TempGoal t : temps == null ? List.<BrainDumpConfirmRequest.TempGoal>of() : temps) {
            if (byKey.putIfAbsent(t.tempKey(), t) != null) throw new CustomException(ErrorCode.BRAIN_DUMP_TEMP_GOAL_UNKNOWN);
        }
        Set<String> keys = new HashSet<>();
        for (BrainDumpConfirmRequest.Item i : items) {
            if (!keys.add(i.clientKey())) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            boolean hasGoal = i.goalCategoryId() != null;
            boolean hasTemp = i.tempGoalKey() != null && !i.tempGoalKey().isBlank();
            if (hasGoal == hasTemp) throw new CustomException(ErrorCode.BRAIN_DUMP_GOAL_REQUIRED);
            if (hasTemp && !byKey.containsKey(i.tempGoalKey())) throw new CustomException(ErrorCode.BRAIN_DUMP_TEMP_GOAL_UNKNOWN);
            if (i.dateType() == DateType.DUE && i.dueDate() == null && i.startTime() == null
                    && i.scheduledDate() == null) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }
        return byKey;
    }

    /**
     * 사용자가 정한 시각이 들어갈 수 있는지.
     *  - 기존 일정 중 자동 배치 블록은 밀어내면 되므로 pushed 에 담는다.
     *  - 잠긴 Task·루틴·완료·미션 일정, 그리고 이번에 같이 입력한 다른 Task 와는 한 시각에 2개까지 같이 둘 수 있다
     *    (드래그와 같은 규칙, Concurrency.MAX_TASKS_AT_ONCE). 이미 2개가 겹친 순간에 놓으려 할 때만 충돌.
     *  - 완료한 Task 는 실제로 한 시간이 자리를 차지한다.
     */
    private List<BrainDumpConfirmResponse.Conflict> findConflicts(Long userId, List<BrainDumpConfirmRequest.Item> items,
                                                                   Map<String, DateTimeRange> explicit, List<Task> pushed) {
        List<BrainDumpConfirmResponse.Conflict> conflicts = new ArrayList<>();
        Map<String, String> titleOf = new HashMap<>();
        items.forEach(i -> titleOf.put(i.clientKey(), i.title()));

        List<Map.Entry<String, DateTimeRange>> list = new ArrayList<>(explicit.entrySet());
        for (int a = 0; a < list.size(); a++) {
            String key = list.get(a).getKey();
            DateTimeRange r = list.get(a).getValue();
            List<DateTimeRange> blockers = new ArrayList<>();
            List<String> names = new ArrayList<>();
            Long firstTaskId = null;

            // 이번에 같이 입력한 다른 Task
            for (int b = 0; b < list.size(); b++) {
                if (b == a || !list.get(b).getValue().overlaps(r)) continue;
                blockers.add(list.get(b).getValue());
                names.add(titleOf.get(list.get(b).getKey()));
            }

            // 기존 일정
            Map<Long, Task> existing = new LinkedHashMap<>();
            for (LocalDate d = r.start().toLocalDate(); !d.isAfter(r.end().minusNanos(1).toLocalDate()); d = d.plusDays(1)) {
                for (Task t : placementQueryRepository.findForPlacement(userId, d, r.start(), r.end())) existing.putIfAbsent(t.getTaskId(), t);
            }
            if (actualRangeReader != null) {
                for (Task t : actualRangeReader.completedWithActualIn(userId, r.start(), r.end())) existing.putIfAbsent(t.getTaskId(), t);
            }
            Map<Long, DateTimeRange> actuals = actualRangeReader != null ? actualRangeReader.actualRanges(existing.values()) : Map.of();
            for (Task t : existing.values()) {
                if (t.getTaskStatus() == TaskStatus.SKIPPED || t.getTaskStatus() == TaskStatus.DELETED) continue;
                DateTimeRange tr = ActualRangeReader.occupied(t, actuals.get(t.getTaskId()), 30);
                if (tr == null || !tr.overlaps(r)) continue;
                if (isMovableEngineBlock(t)) {
                    if (pushed.stream().noneMatch(p -> p.getTaskId().equals(t.getTaskId()))) pushed.add(t);
                } else {
                    blockers.add(tr);
                    names.add(t.getTaskName());
                    if (firstTaskId == null) firstTaskId = t.getTaskId();
                }
            }

            if (Concurrency.maxOverlap(blockers, r) >= Concurrency.MAX_TASKS_AT_ONCE) {
                conflicts.add(new BrainDumpConfirmResponse.Conflict(key, titleOf.get(key), r.start(), r.end(),
                        firstTaskId, String.join("·", names)));
            }
        }
        return conflicts;
    }

    private Map<String, GoalCategory> createTempGoals(User user, List<BrainDumpConfirmRequest.Item> items,
                                                     Map<String, BrainDumpConfirmRequest.TempGoal> inputs, LocalDate today) {
        Set<String> used = new LinkedHashSet<>();
        items.forEach(i -> { if (i.tempGoalKey() != null) used.add(i.tempGoalKey()); });
        if (used.isEmpty()) return Map.of();

        long owned = goalCategoryRepository.countTemporaryByUserId(user.getUserId());
        if (owned + used.size() > MAX_TEMP_GOALS_OWNED) {
            throw new CustomException(ErrorCode.TEMP_GOAL_LIMIT_EXCEEDED);
        }
        Map<String, GoalCategory> result = new LinkedHashMap<>();
        for (String key : used) {
            BrainDumpConfirmRequest.TempGoal in = inputs.get(key);
            // 하나씩 저장해야 다음 색 배정 때 "이미 쓰는 색"으로 잡혀 서로 다른 색이 된다
            GoalCategory goal = GoalCategory.createTemporary(user, colorAssigner.assign(user, in.colorId()),
                    in.name().strip(), in.emoji() != null && !in.emoji().isBlank() ? in.emoji().strip() : "📌", today);
            goalCategoryRepository.save(goal);
            result.put(key, goal);
        }
        return result;
    }

    private GoalCategory findGoal(User user, Long goalCategoryId) {
        GoalCategory goal = goalCategoryRepository.findByGoalCategoryIdAndUser(goalCategoryId, user, GoalCategoryStatus.DELETED)
                .orElseThrow(() -> new CustomException(ErrorCode.GOAL_CATEGORY_NOT_FOUND));
        if (goal.getGoalCategoryStatus() == GoalCategoryStatus.COMPLETED) {
            throw new CustomException(ErrorCode.GOAL_CATEGORY_NOT_FOUND); // 완료된 목표에는 새 Task 를 붙이지 않는다
        }
        return goal;
    }

    /** 세부 목표를 골랐으면 그 목표 소속인지 확인, 안 골랐으면 목표의 "기타 할 일" (없으면 만든다) */
    private GeneralCategory resolveGeneral(User user, GoalCategory goal, Long generalCategoryId, Map<Long, GeneralCategory> cache) {
        if (generalCategoryId != null) {
            GeneralCategory g = generalCategoryRepository.findByGeneralCategoryIdAndUser(generalCategoryId, user, GoalCategoryStatus.DELETED)
                    .orElseThrow(() -> new CustomException(ErrorCode.GENERAL_CATEGORY_NOT_FOUND));
            if (!g.getGoalCategory().getGoalCategoryId().equals(goal.getGoalCategoryId())) {
                throw new CustomException(ErrorCode.GENERAL_CATEGORY_NOT_FOUND);
            }
            return g;
        }
        return cache.computeIfAbsent(goal.getGoalCategoryId(), id -> {
            if (goal.getGoalCategoryId() != null) {
                List<GeneralCategory> existing = generalCategoryRepository.findDefaultBuckets(goal.getGoalCategoryId());
                if (!existing.isEmpty()) return existing.get(0);
            }
            return generalCategoryRepository.save(GeneralCategory.createDefaultBucket(goal));
        });
    }

    private Milestone resolveMilestone(GoalCategory goal, Long milestoneId) {
        if (milestoneId == null) return null;
        return goal.getMilestones().stream()
                .filter(m -> milestoneId.equals(m.getMilestoneId()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.BRAIN_DUMP_MILESTONE_MISMATCH));
    }

    // ── 유틸 ─────────────────────────────────────────────

    private static BrainDumpConfirmResponse.Placement toPlacement(PlacementOutcome.PlacedBlock b, Map<Long, Task> created,
                                                                  Map<Long, Task> pushed, Map<Long, String> clientKeyOf) {
        Task t = created.containsKey(b.taskId()) ? created.get(b.taskId()) : pushed.get(b.taskId());
        return new BrainDumpConfirmResponse.Placement(b.taskId(), clientKeyOf.get(b.taskId()),
                t != null ? t.getTaskName() : null, b.date(), b.start(), b.end(), b.reason(), false);
    }

    static boolean isMovableEngineBlock(Task t) {
        return !t.isScheduleLocked() && t.getPlacedBy() == PlacedBy.ENGINE
                && t.getTaskStatus() == TaskStatus.TODO && !t.isMissionTask();
    }

    private static int minutesOf(BrainDumpConfirmRequest.Item i) {
        return i.estimatedMinutes() != null ? i.estimatedMinutes() : 30;
    }

    private static LocalDate clamp(LocalDate d, LocalDate today) {
        return d == null || d.isBefore(today) ? today : d;
    }
}
