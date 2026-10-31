package com.naleul.naleul.domain.goalEdit.service;

import com.naleul.naleul.domain.aiQuality.service.AiSuggestionLogWriter;
import com.naleul.naleul.domain.aiUsage.enums.AiFeature;
import com.naleul.naleul.domain.aiUsage.quota.AiQuotaService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naleul.naleul.domain.generalCategory.dto.response.GeneralCategoryResponse.RoutineSummary;
import com.naleul.naleul.domain.goalCategory.dto.request.GoalCategoryUpdateRequest;
import com.naleul.naleul.domain.goalCategory.dto.response.GoalCategoryResponse;
import com.naleul.naleul.domain.goalCategory.dto.response.GoalCategoryResponse.GeneralCategoryInfo;
import com.naleul.naleul.domain.goalCategory.dto.response.GoalCategoryResponse.MilestoneInfo;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalCategory.reader.GoalCategoryReader;
import com.naleul.naleul.domain.goalCategory.service.GoalCategoryService;
import com.naleul.naleul.domain.goalCreation.client.GoalAiClient;
import com.naleul.naleul.domain.goalCreation.client.GoalAiException;
import com.naleul.naleul.domain.goalCreation.client.GoalEditAiRequest;
import com.naleul.naleul.domain.goalCreation.client.GoalEditAiResult;
import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.goalEdit.dto.GoalEditOp;
import com.naleul.naleul.domain.goalEdit.dto.GoalEditOpType;
import com.naleul.naleul.domain.goalEdit.dto.GoalEditPreviewResponse;
import com.naleul.naleul.domain.goalEdit.dto.GoalEditPreviewResponse.Change;
import com.naleul.naleul.domain.goalProgress.entity.GoalMetricLog;
import com.naleul.naleul.domain.goalProgress.entity.GoalReflection;
import com.naleul.naleul.domain.goalProgress.repository.GoalMetricLogRepository;
import com.naleul.naleul.domain.goalProgress.repository.GoalReflectionRepository;
import com.naleul.naleul.domain.milestone.dto.request.MilestoneCreateRequest;
import com.naleul.naleul.domain.milestone.dto.request.MilestoneUpdateRequest;
import com.naleul.naleul.domain.milestone.service.MilestoneService;
import com.naleul.naleul.domain.planner.dto.request.GoalTaskRequest;
import com.naleul.naleul.domain.planner.service.GoalTaskCommandService;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.routine.dto.request.RoutineCreateRequest;
import com.naleul.naleul.domain.routine.dto.request.RoutineUpdateRequest;
import com.naleul.naleul.domain.routine.service.RoutineService;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 목표 상세 "말로 고치기".
 *
 * 왜 필요한가: 상세 화면에서 무언가를 고치려면 세부 목표 → 루틴 → 마일스톤 트리와 수치 계산 방식을 알아야 했다.
 * "운동은 주 2회로 줄여줘"처럼 한 문장으로 말하면 AI 가 변경안을 만들고, 사용자가 확인한 것만 적용한다.
 *
 *  1) preview: 지금 목표 상태(id 포함) + 문장 → AI → 변경안 검증(이 목표의 id 인지, 값이 말이 되는지) → 사람이 읽는 전/후 문장
 *  2) apply  : 사용자가 고른 변경만 기존 서비스(목표·루틴·점검 시점·Task)로 한 트랜잭션에서 적용
 *     → 기존 서비스의 검증(무료 한도, 시간 범위 등)과 점검 시점 자동 보정이 그대로 적용된다.
 *
 * 실행 중인 목표라 바로 바꾸지 않고 반드시 미리보기를 거친다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalEditService {

    /** AI 관심도: 한도에 막힌 시도 (V70) */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.naleul.naleul.domain.aiUsage.limit.AiLimitHitRecorder limitHits;

    static final int MAX_OPS = 20;
    static final int MIN_MINUTES = 5;
    static final int MAX_ROUTINE_MINUTES = 24 * 60; // 2026-10 사용자 요청: 루틴 길이 제한 없음 (하루 안)
    static final int MAX_TASK_MINUTES = 720;
    static final int TITLE_MAX = 40;
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("M월 d일");

    private final GoalCategoryReader goalCategoryReader;
    private final GoalCategoryService goalCategoryService;
    private final RoutineService routineService;
    private final MilestoneService milestoneService;
    private final GoalTaskCommandService goalTaskCommandService;
    private final GoalAiClient goalAiClient;
    private final AiQuotaService aiQuotaService;
    private final ObjectMapper objectMapper;
    private final PlannerClock clock;
    /** 최근 기록 · 회고 — "마라탕 먹음" 같은 하루 기록을 근거로 계획을 조정하게 */
    private final GoalMetricLogRepository metricLogRepository;
    private final GoalReflectionRepository reflectionRepository;

    /** AI 품질 지표: 제안 수 · 적용 수 (V64) */
    @Autowired(required = false)
    private AiSuggestionLogWriter suggestionLog;

    /** AI 에 보여줄 최근 기록 · 회고 기간 */
    static final int RECENT_DAYS = 14;

    // ───────────────────── 미리보기 ─────────────────────

    /** 트랜잭션 없음: AI 호출 동안 DB 커넥션을 잡지 않는다 */
    public GoalEditPreviewResponse preview(Long userId, Long goalCategoryId, String instruction) {
        Snapshot snap = snapshot(userId, goalCategoryId);
        Long usageId = aiQuotaService.tryConsume(userId, AiFeature.GOAL_EDIT);
        if (usageId == null) {
            if (limitHits != null) limitHits.record(userId, "GOAL_EDIT", com.naleul.naleul.domain.aiUsage.limit.AiLimitHitRecorder.Reason.DAILY);
            throw new CustomException(ErrorCode.AI_EDIT_DAILY_LIMIT_EXCEEDED);
        }
        GoalEditAiResult ai;
        try {
            ai = goalAiClient.suggestGoalEdit(new GoalEditAiRequest(clock.today(),
                    contextJson(snap, recentDays(goalCategoryId, clock.today())), instruction.strip()));
        } catch (GoalAiException e) {
            log.warn("[GoalEdit] AI 실패 goalId={} reason={}", goalCategoryId, e.getReason());
            aiQuotaService.refund(usageId); // AI 가 안 돼서 못 쓴 건 세지 않는다
            throw new CustomException(ErrorCode.AI_UNAVAILABLE);
        } catch (RuntimeException e) {
            aiQuotaService.refund(usageId);
            throw e;
        }
        List<Change> changes = new ArrayList<>();
        for (GoalEditOp op : ai.ops().stream().limit(MAX_OPS).toList()) {
            GoalEditOp clean = sanitize(op, snap, clock.today());
            if (clean != null) changes.add(describe(clean, snap));
        }
        String summary = ai.summary() != null && !ai.summary().isBlank() ? ai.summary().strip()
                : changes.isEmpty() ? "바꿀 내용을 찾지 못했어요. 조금 더 구체적으로 적어 주세요." : "이렇게 바꿀게요.";
        log.info("[GoalEdit] 미리보기 goalId={} aiOps={} validOps={}", goalCategoryId, ai.ops().size(), changes.size());
        if (suggestionLog != null) {
            suggestionLog.suggested(userId, AiFeature.GOAL_EDIT, goalCategoryId, true, ai.ops().size(), changes.size(), null);
        }
        return new GoalEditPreviewResponse(summary, changes);
    }

    // ───────────────────── 적용 ─────────────────────

    @Transactional
    public int apply(Long userId, Long goalCategoryId, List<GoalEditOp> ops) {
        Snapshot snap = snapshot(userId, goalCategoryId);
        LocalDate today = clock.today();
        List<GoalEditOp> clean = new ArrayList<>();
        for (GoalEditOp op : ops) {
            GoalEditOp c = sanitize(op, snap, today);
            if (c == null) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);   // 미리보기 뒤에 목표가 바뀐 경우 등
            clean.add(c);
        }
        // 목표(기간·수치)를 먼저 바꿔야 새 점검 시점·루틴 기간이 새 종료일 기준으로 잡힌다
        clean.sort((a, b) -> Boolean.compare(b.type() == GoalEditOpType.UPDATE_GOAL, a.type() == GoalEditOpType.UPDATE_GOAL));
        LocalDate end = snap.goal().getGoalCategoryEndDate();
        for (GoalEditOp op : clean) {
            if (op.type() == GoalEditOpType.UPDATE_GOAL && op.date() != null) end = op.date();
            execute(userId, goalCategoryId, op, snap, end, today);
        }
        log.info("[GoalEdit] 적용 goalId={} ops={}", goalCategoryId, clean.size());
        if (suggestionLog != null) {
            suggestionLog.applied(userId, AiFeature.GOAL_EDIT, goalCategoryId, clean.size(), null, null, null,
                    List.of(), clean.stream().filter(o -> o.type() == GoalEditOpType.UPDATE_ROUTINE)
                            .map(GoalEditOp::targetId).toList());
        }
        return clean.size();
    }

    private void execute(Long userId, Long goalId, GoalEditOp op, Snapshot snap, LocalDate goalEnd, LocalDate today) {
        switch (op.type()) {
            case UPDATE_GOAL -> goalCategoryService.update(goalId, userId, GoalCategoryUpdateRequest.builder()
                    .goalCategoryName(op.title())
                    .goalCategoryEndDate(op.date())
                    .startValue(op.startValue())
                    .targetValue(op.targetValue())
                    .build());
            case ADD_ROUTINE -> {
                LocalDate start = snap.goal().getGoalCategoryStartDate() != null
                        && snap.goal().getGoalCategoryStartDate().isAfter(today) ? snap.goal().getGoalCategoryStartDate() : today;
                LocalDate end = goalEnd != null && !goalEnd.isBefore(start) ? goalEnd : start.plusDays(90);
                // 영역을 안 고르면 이 목표의 첫 영역에 넣는다 (AI 목표는 영역이 하나뿐이라 대부분 그 영역)
                Long areaId = op.areaId() != null ? op.areaId()
                        : snap.areas().keySet().stream().findFirst().orElse(null);
                routineService.create(RoutineCreateRequest.builder()
                        .goalCategoryId(goalId)
                        .generalCategoryId(areaId)
                        .routineName(op.title())
                        .repeatStartDate(start)
                        .repeatEndDate(end)
                        .repeatDays(toDays(op.days()))
                        .repeatStartTime(op.startTime())
                        .repeatEndTime(endTime(op.startTime(), op.durationMinutes()))
                        .description(op.description())
                        .build(), userId);
            }
            case UPDATE_ROUTINE -> {
                RoutineSummary current = snap.routines().get(op.targetId());
                LocalTime start = op.startTime() != null ? op.startTime() : current.getRepeatStartTime();
                Integer minutes = op.durationMinutes() != null ? op.durationMinutes() : minutesOf(current);
                boolean timeChanged = op.startTime() != null || (op.durationMinutes() != null && start != null);
                routineService.update(userId, op.targetId(), RoutineUpdateRequest.builder()
                        .routineName(op.title())
                        .repeatDays(op.days() != null ? toDays(op.days()) : null)
                        .repeatStartTime(timeChanged ? start : null)
                        .repeatEndTime(timeChanged ? endTime(start, minutes) : null)
                        .description(op.description())
                        .build());
            }
            case DELETE_ROUTINE -> routineService.delete(op.targetId(), userId);
            case ADD_MILESTONE -> milestoneService.create(userId, goalId,
                    new MilestoneCreateRequest(op.title(), null, op.date(), null));
            case UPDATE_MILESTONE -> milestoneService.update(userId, op.targetId(),
                    new MilestoneUpdateRequest(op.title(), null, op.date(), null, null, null, null));
            case DELETE_MILESTONE -> milestoneService.delete(userId, op.targetId());
            case ADD_TASK -> {
                LocalTime start = op.startTime();
                int minutes = op.durationMinutes() != null ? op.durationMinutes() : 30;
                goalTaskCommandService.create(userId, goalId, new GoalTaskRequest(op.title(), null, op.areaId(), null,
                        op.date(), start, start != null ? start.plusMinutes(minutes) : null, minutes, null));
            }
        }
    }

    // ───────────────────── 검증 ─────────────────────

    /** 지금 목표 상태 (id 확인 · 전/후 문장용) */
    record Snapshot(GoalCategoryResponse goal, Map<Long, RoutineSummary> routines, Map<Long, Long> routineArea,
                    Map<Long, MilestoneInfo> milestones, Map<Long, GeneralCategoryInfo> areas) {
    }

    private Snapshot snapshot(Long userId, Long goalCategoryId) {
        GoalCategoryResponse goal = goalCategoryReader.getGoalCategory(goalCategoryId, userId);   // 내 목표가 아니면 404
        if (goal.getGoalCategoryStatus() == GoalCategoryStatus.COMPLETED || goal.getGoalCategoryStatus() == GoalCategoryStatus.DELETED) {
            throw new CustomException(ErrorCode.GOAL_CATEGORY_NOT_FOUND);
        }
        Map<Long, RoutineSummary> routines = new LinkedHashMap<>();
        Map<Long, Long> routineArea = new LinkedHashMap<>();
        Map<Long, GeneralCategoryInfo> areas = new LinkedHashMap<>();
        for (GeneralCategoryInfo area : goal.getGeneralCategories() == null ? List.<GeneralCategoryInfo>of() : goal.getGeneralCategories()) {
            if (area.getGeneralCategoryStatus() == GoalCategoryStatus.DELETED) continue;
            areas.put(area.getGeneralCategoryId(), area);
            for (RoutineSummary r : area.getRoutines() == null ? List.<RoutineSummary>of() : area.getRoutines()) {
                routines.put(r.getRoutineId(), r);
                routineArea.put(r.getRoutineId(), area.getGeneralCategoryId());
            }
        }
        Map<Long, MilestoneInfo> milestones = new LinkedHashMap<>();
        if (goal.getMilestones() != null) for (MilestoneInfo m : goal.getMilestones()) milestones.put(m.getMilestoneId(), m);
        return new Snapshot(goal, routines, routineArea, milestones, areas);
    }

    /**
     * AI(또는 클라이언트)가 보낸 변경 하나를 검사하고 다듬는다. 쓸 수 없으면 null.
     * 이 목표의 루틴·점검 시점 id 인지, 날짜·시간·길이가 말이 되는지만 본다 (나머지는 각 서비스가 검증).
     */
    static GoalEditOp sanitize(GoalEditOp op, Snapshot snap, LocalDate today) {
        if (op == null || op.type() == null) return null;
        String title = trimTitle(op.title());
        Long areaId = op.areaId() != null && snap.areas().containsKey(op.areaId()) ? op.areaId() : null;
        Integer minutes = op.durationMinutes();
        List<Weekday> days = op.days() == null ? null : op.days().stream().filter(Objects::nonNull).distinct().toList();
        LocalDate goalStart = snap.goal().getGoalCategoryStartDate();
        LocalDate goalEnd = snap.goal().getGoalCategoryEndDate();

        return switch (op.type()) {
            case UPDATE_GOAL -> {
                LocalDate end = op.date();
                if (end != null && (end.isBefore(today) || (goalStart != null && !end.isAfter(goalStart)))) end = null;
                boolean hasMetric = snap.goal().getTargetValue() != null;
                BigDecimal sv = hasMetric ? op.startValue() : null;
                BigDecimal tv = hasMetric ? op.targetValue() : null;
                if (title == null && end == null && sv == null && tv == null) yield null;
                yield new GoalEditOp(op.type(), null, title, null, null, null, null, end, sv, tv, null);
            }
            case ADD_ROUTINE -> {
                if (title == null || days == null || days.isEmpty()) yield null;
                Integer m = minutes == null ? null : clamp(minutes, MIN_MINUTES, MAX_ROUTINE_MINUTES);
                yield new GoalEditOp(op.type(), null, title, m, days, op.startTime(), trimDesc(op.description()),
                        null, null, null, areaId);
            }
            case UPDATE_ROUTINE -> {
                if (op.targetId() == null || !snap.routines().containsKey(op.targetId())) yield null;
                Integer m = minutes == null ? null : clamp(minutes, MIN_MINUTES, MAX_ROUTINE_MINUTES);
                List<Weekday> d = days != null && !days.isEmpty() ? days : null;
                if (title == null && m == null && d == null && op.startTime() == null && op.description() == null) yield null;
                yield new GoalEditOp(op.type(), op.targetId(), title, m, d, op.startTime(), trimDesc(op.description()),
                        null, null, null, null);
            }
            case DELETE_ROUTINE -> op.targetId() != null && snap.routines().containsKey(op.targetId())
                    ? new GoalEditOp(op.type(), op.targetId(), null, null, null, null, null, null, null, null, null) : null;
            case ADD_MILESTONE -> {
                if (title == null || op.date() == null || op.date().isBefore(today)) yield null;
                if (goalEnd != null && op.date().isAfter(goalEnd)) yield null;
                yield new GoalEditOp(op.type(), null, title, null, null, null, null, op.date(), null, null, null);
            }
            case UPDATE_MILESTONE -> {
                if (op.targetId() == null || !snap.milestones().containsKey(op.targetId())) yield null;
                LocalDate date = op.date() != null && !op.date().isBefore(today)
                        && (goalEnd == null || !op.date().isAfter(goalEnd)) ? op.date() : null;
                if (title == null && date == null) yield null;
                yield new GoalEditOp(op.type(), op.targetId(), title, null, null, null, null, date, null, null, null);
            }
            case DELETE_MILESTONE -> op.targetId() != null && snap.milestones().containsKey(op.targetId())
                    ? new GoalEditOp(op.type(), op.targetId(), null, null, null, null, null, null, null, null, null) : null;
            case ADD_TASK -> {
                if (title == null || op.date() == null || op.date().isBefore(today)) yield null;
                Integer m = clamp(minutes != null ? minutes : 30, MIN_MINUTES, MAX_TASK_MINUTES);
                yield new GoalEditOp(op.type(), null, title, m, null, op.startTime(), null, op.date(), null, null, areaId);
            }
        };
    }

    // ───────────────────── 사람이 읽는 전/후 ─────────────────────

    static Change describe(GoalEditOp op, Snapshot snap) {
        GoalCategoryResponse g = snap.goal();
        String unit = g.getMetricUnit() != null ? g.getMetricUnit() : "";
        return switch (op.type()) {
            case UPDATE_GOAL -> {
                List<String> before = new ArrayList<>(), after = new ArrayList<>();
                if (op.title() != null) { before.add(g.getGoalCategoryName()); after.add(op.title()); }
                if (op.date() != null) { before.add("종료 " + md(g.getGoalCategoryEndDate())); after.add("종료 " + md(op.date())); }
                if (op.startValue() != null) { before.add("시작 " + num(g.getStartValue()) + unit); after.add("시작 " + num(op.startValue()) + unit); }
                if (op.targetValue() != null) { before.add("목표 " + num(g.getTargetValue()) + unit); after.add("목표 " + num(op.targetValue()) + unit); }
                yield new Change(op, "변경", "목표", String.join(" · ", before), String.join(" · ", after)
                        + (op.date() != null || op.startValue() != null || op.targetValue() != null ? " (점검 시점은 자동으로 다시 맞춰요)" : ""));
            }
            case ADD_ROUTINE -> new Change(op, "추가", "루틴", null, routineText(op.title(), op.days(), op.startTime(), op.durationMinutes()));
            case UPDATE_ROUTINE -> {
                RoutineSummary r = snap.routines().get(op.targetId());
                List<Weekday> curDays = r.getRepeatDays() == null ? List.of() : r.getRepeatDays().stream().map(Weekday::from).toList();
                String before = routineText(r.getRoutineName(), curDays, r.getRepeatStartTime(), minutesOf(r));
                String after = routineText(op.title() != null ? op.title() : r.getRoutineName(),
                        op.days() != null ? op.days() : curDays,
                        op.startTime() != null ? op.startTime() : r.getRepeatStartTime(),
                        op.durationMinutes() != null ? op.durationMinutes() : minutesOf(r));
                if (op.description() != null) after += " · 방법: " + op.description();
                yield new Change(op, "변경", "루틴 '" + r.getRoutineName() + "'", before, after);
            }
            case DELETE_ROUTINE -> {
                RoutineSummary r = snap.routines().get(op.targetId());
                yield new Change(op, "삭제", "루틴 '" + r.getRoutineName() + "'", routineText(r.getRoutineName(),
                        r.getRepeatDays() == null ? List.of() : r.getRepeatDays().stream().map(Weekday::from).toList(),
                        r.getRepeatStartTime(), minutesOf(r)), null);
            }
            case ADD_MILESTONE -> new Change(op, "추가", "점검 시점", null, md(op.date()) + " · " + op.title());
            case UPDATE_MILESTONE -> {
                MilestoneInfo m = snap.milestones().get(op.targetId());
                yield new Change(op, "변경", "점검 시점 '" + m.getTitle() + "'", md(m.getDueDate()) + " · " + m.getTitle(),
                        md(op.date() != null ? op.date() : m.getDueDate()) + " · " + (op.title() != null ? op.title() : m.getTitle()));
            }
            case DELETE_MILESTONE -> {
                MilestoneInfo m = snap.milestones().get(op.targetId());
                yield new Change(op, "삭제", "점검 시점 '" + m.getTitle() + "'", md(m.getDueDate()) + " · " + m.getTitle(), null);
            }
            case ADD_TASK -> new Change(op, "추가", "할 일", null, md(op.date())
                    + (op.startTime() != null ? " " + op.startTime() : "") + " · " + op.title() + " (" + op.durationMinutes() + "분)");
        };
    }

    // ───────────────────── AI 에 줄 목표 상태 ─────────────────────

    String contextJson(Snapshot snap) {
        return contextJson(snap, List.of());
    }

    /**
     * 최근 RECENT_DAYS 일의 하루 기록 (날짜순): 수치 · 기분 · 무엇을 했는지.
     * 예) {"date":"2026-10-08","value":64.0,"mood":"BAD","note":"점심에 마라탕 먹음"}
     */
    List<Map<String, Object>> recentDays(Long goalId, LocalDate today) {
        LocalDate from = today.minusDays(RECENT_DAYS);
        Map<LocalDate, Map<String, Object>> byDate = new TreeMap<>();
        for (GoalMetricLog l : metricLogRepository.findAllOfGoal(goalId)) {
            if (l.getLogDate().isBefore(from)) continue;
            Map<String, Object> d = byDate.computeIfAbsent(l.getLogDate(), k -> new LinkedHashMap<>(Map.of("date", k)));
            d.put("value", l.getValue());
            if (l.getMemo() != null) d.put("memo", l.getMemo());
        }
        for (GoalReflection r : reflectionRepository.findRecent(goalId, from)) {
            Map<String, Object> d = byDate.computeIfAbsent(r.getReflectDate(), k -> new LinkedHashMap<>(Map.of("date", k)));
            if (r.getMood() != null) d.put("mood", r.getMood().name());
            if (r.getNote() != null) d.put("note", r.getNote());
        }
        return new ArrayList<>(byDate.values());
    }

    String contextJson(Snapshot snap, List<Map<String, Object>> recentDays) {
        GoalCategoryResponse g = snap.goal();
        Map<String, Object> goal = new LinkedHashMap<>();
        goal.put("title", g.getGoalCategoryName());
        goal.put("startDate", g.getGoalCategoryStartDate());
        goal.put("endDate", g.getGoalCategoryEndDate());
        if (g.getTargetValue() != null) {
            Map<String, Object> metric = new LinkedHashMap<>();
            metric.put("name", Objects.toString(g.getMetricName(), ""));
            metric.put("unit", Objects.toString(g.getMetricUnit(), ""));
            metric.put("startValue", Objects.toString(g.getStartValue(), ""));
            metric.put("currentValue", Objects.toString(g.getCurrentValue(), ""));
            metric.put("targetValue", g.getTargetValue());
            goal.put("metric", metric);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("goal", goal);
        // Map.of 는 값이 null 이면 NullPointerException → 이름 · 날짜가 비어 있는 목표에서 "말로 고치기"가 500 으로 실패했다
        root.put("areas", snap.areas().values().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getGeneralCategoryId());
            m.put("name", a.isDefaultBucket() ? "(영역 없음)" : Objects.toString(a.getGeneralCategoryName(), ""));
            return m;
        }).toList());
        root.put("routines", snap.routines().values().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getRoutineId());
            m.put("title", r.getRoutineName());
            m.put("days", r.getRepeatDays() == null ? List.of() : r.getRepeatDays().stream().map(Weekday::from).toList());
            m.put("startTime", r.getRepeatStartTime());
            m.put("durationMinutes", minutesOf(r));
            m.put("description", r.getDescription());
            m.put("areaId", snap.routineArea().get(r.getRoutineId()));
            return m;
        }).toList());
        root.put("milestones", snap.milestones().values().stream().map(m -> {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", m.getMilestoneId());
            o.put("title", Objects.toString(m.getTitle(), ""));
            o.put("date", m.getDueDate());
            o.put("status", m.getStatus());
            return o;
        }).toList());
        // 최근 하루 기록 · 회고 (사용자가 직접 남긴 것). 계획을 조정할 근거로만 쓰고 그대로 따라 하지 않는다
        if (!recentDays.isEmpty()) root.put("recentDays", recentDays);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // ───────────────────── 유틸 ─────────────────────

    static Integer minutesOf(RoutineSummary r) {
        if (r.getDurationMinutes() != null) return r.getDurationMinutes();
        if (r.getRepeatStartTime() != null && r.getRepeatEndTime() != null) {
            long m = java.time.Duration.between(r.getRepeatStartTime(), r.getRepeatEndTime()).toMinutes();
            return (int) (m <= 0 ? m + 24 * 60 : m);   // 자정을 넘기는 루틴
        }
        return null;
    }

    static LocalTime endTime(LocalTime start, Integer minutes) {
        if (start == null) return null;
        LocalTime end = start.plusMinutes(minutes != null ? minutes : 30);
        return end.isBefore(start) ? LocalTime.of(23, 59) : end;   // 자정을 넘기는 루틴은 아직 지원 안 함
    }

    private static List<DayOfWeek> toDays(List<Weekday> days) {
        return days == null ? new ArrayList<>() : days.stream().map(Weekday::toDayOfWeek).collect(Collectors.toCollection(ArrayList::new));
    }

    private static String routineText(String title, List<Weekday> days, LocalTime start, Integer minutes) {
        String d = days == null || days.isEmpty() ? "" : days.size() == 7 ? "매일" : days.stream()
                .sorted().map(GoalEditService::dayLabel).collect(Collectors.joining("·"));
        StringBuilder sb = new StringBuilder(title);
        if (!d.isEmpty()) sb.append(" · ").append(d);
        if (start != null) sb.append(" ").append(start);
        if (minutes != null) sb.append(" · ").append(minutes).append("분");
        return sb.toString();
    }

    private static String dayLabel(Weekday w) {
        return switch (w) {
            case MON -> "월"; case TUE -> "화"; case WED -> "수"; case THU -> "목";
            case FRI -> "금"; case SAT -> "토"; case SUN -> "일";
        };
    }

    private static String md(LocalDate d) {
        return d == null ? "없음" : d.format(MD);
    }

    private static String num(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }

    private static String trimTitle(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.strip();
        return t.length() > TITLE_MAX ? t.substring(0, TITLE_MAX) : t;
    }

    private static String trimDesc(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.length() > 300 ? t.substring(0, 300) : t;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
