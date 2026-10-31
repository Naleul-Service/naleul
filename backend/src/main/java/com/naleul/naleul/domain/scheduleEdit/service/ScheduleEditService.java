package com.naleul.naleul.domain.scheduleEdit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naleul.naleul.domain.aiQuality.service.AiSuggestionLogWriter;
import com.naleul.naleul.domain.aiUsage.enums.AiFeature;
import com.naleul.naleul.domain.aiUsage.quota.AiQuotaService;
import com.naleul.naleul.domain.goalCreation.client.GoalAiClient;
import com.naleul.naleul.domain.goalCreation.client.GoalAiException;
import com.naleul.naleul.domain.goalCreation.client.ScheduleEditAiRequest;
import com.naleul.naleul.domain.goalCreation.client.ScheduleEditAiResult;
import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.lifePattern.enums.LifePatternType;
import com.naleul.naleul.domain.planner.dto.response.TimeBlockTaskResponse;
import com.naleul.naleul.domain.planner.dto.response.TimetableResponse;
import com.naleul.naleul.domain.planner.service.TaskScheduleService;
import com.naleul.naleul.domain.planner.service.TimetableQueryService;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.routine.dto.request.RoutineUpdateRequest;
import com.naleul.naleul.domain.routine.dto.response.RoutineResponse;
import com.naleul.naleul.domain.routine.enums.RoutineStatus;
import com.naleul.naleul.domain.routine.reader.RoutineReader;
import com.naleul.naleul.domain.routine.service.RoutineService;
import com.naleul.naleul.domain.scheduleEdit.dto.ScheduleEditApplyResponse;
import com.naleul.naleul.domain.scheduleEdit.dto.ScheduleEditOp;
import com.naleul.naleul.domain.scheduleEdit.dto.ScheduleEditOpType;
import com.naleul.naleul.domain.scheduleEdit.dto.ScheduleEditPreviewResponse;
import com.naleul.naleul.domain.scheduleEdit.dto.ScheduleEditPreviewResponse.Change;
import com.naleul.naleul.domain.task.enums.TaskSourceType;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 캘린더 "AI로 일정 조정".
 *
 * 왜 필요한가: 루틴은 목표마다 따로 만들어서, 다른 목표의 루틴과 같은 시간에 겹쳐도 만들 때는 알기 어렵다.
 * 겹친 루틴이나 여러 일정을 한꺼번에 옮기려면 캘린더에서 하나씩 끌어야 했다.
 * "겹치는 루틴 정리해줘", "이번 주 운동은 전부 저녁 8시로" 처럼 한 문장으로 말하면 AI 가 변경안을 만들고,
 * 사용자가 확인한 것만 적용한다 (목표 상세 "말로 고치기"와 같은 미리보기 → 적용 흐름).
 *
 *  1) preview: 루틴 전부 + 보고 있는 기간의 Task · 하루 범위 · 고정 시간 + 겹치는 루틴 → AI → 변경안 검증 → 전/후 문장
 *  2) apply  : 고른 변경만 기존 서비스로 적용 (루틴 = RoutineService.update, Task = TaskScheduleService.tryReschedule)
 *     잠긴 일정과 겹치는 Task 는 건너뛰고 skipped 로 알려준다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleEditService {

    /** AI 관심도: 한도에 막힌 시도 (V70) */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.naleul.naleul.domain.aiUsage.limit.AiLimitHitRecorder limitHits;

    static final int MAX_OPS = 30;
    static final int MAX_TASKS_IN_CONTEXT = 150;
    static final int MIN_MINUTES = 5;
    static final int MAX_ROUTINE_MINUTES = 24 * 60; // 2026-10 사용자 요청: 루틴 길이 제한 없음 (하루 안)
    static final int MAX_TASK_MINUTES = 720;
    /** MOVE_TASK 로 옮길 수 있는 날: 오늘 ~ 60일 뒤 */
    static final int MAX_DAYS_AHEAD = 60;
    static final int DEFAULT_ROUTINE_MINUTES = 30;
    private static final String[] DAY_KO = {"월", "화", "수", "목", "금", "토", "일"};

    private final RoutineReader routineReader;
    private final RoutineService routineService;
    private final TimetableQueryService timetableQueryService;
    private final TaskScheduleService taskScheduleService;
    private final GoalAiClient goalAiClient;
    private final AiQuotaService aiQuotaService;
    private final ObjectMapper objectMapper;
    private final PlannerClock clock;
    /** AI 품질 지표: 제안 수 · 적용 수 · 옮긴 Task/루틴 (V64) */
    @Autowired(required = false)
    private AiSuggestionLogWriter suggestionLog;

    // ───────────────────── 미리보기 ─────────────────────

    /** 트랜잭션 없음: AI 호출 동안 DB 커넥션을 잡지 않는다 */
    public ScheduleEditPreviewResponse preview(Long userId, String instruction, LocalDate startDate, LocalDate endDate) {
        Snapshot snap = snapshot(userId, startDate, endDate);
        List<Overlap> overlaps = overlaps(snap.routines().values());
        Long usageId = aiQuotaService.tryConsume(userId, AiFeature.SCHEDULE_EDIT);
        if (usageId == null) {
            if (limitHits != null) limitHits.record(userId, "SCHEDULE_EDIT", com.naleul.naleul.domain.aiUsage.limit.AiLimitHitRecorder.Reason.DAILY);
            throw new CustomException(ErrorCode.AI_SCHEDULE_EDIT_DAILY_LIMIT_EXCEEDED);
        }
        ScheduleEditAiResult ai;
        try {
            ai = goalAiClient.suggestScheduleEdit(new ScheduleEditAiRequest(snap.today(),
                    contextJson(snap, overlaps), instruction.strip()));
        } catch (GoalAiException e) {
            log.warn("[ScheduleEdit] AI 실패 userId={} reason={}", userId, e.getReason());
            aiQuotaService.refund(usageId); // AI 가 안 돼서 못 쓴 건 세지 않는다
            throw new CustomException(ErrorCode.AI_UNAVAILABLE);
        } catch (RuntimeException e) {
            aiQuotaService.refund(usageId);
            throw e;
        }
        List<Change> changes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ScheduleEditOp op : ai.ops().stream().limit(MAX_OPS).toList()) {
            ScheduleEditOp clean = sanitize(op, snap);
            // 같은 대상을 두 번 바꾸면 처음 것만
            if (clean != null && seen.add(clean.type() + "-" + clean.targetId())) changes.add(describe(clean, snap));
        }
        String summary = ai.summary() != null && !ai.summary().isBlank() ? ai.summary().strip()
                : changes.isEmpty() ? "바꿀 내용을 찾지 못했어요. 조금 더 구체적으로 적어 주세요." : "이렇게 바꿀게요.";
        log.info("[ScheduleEdit] 미리보기 userId={} aiOps={} validOps={}", userId, ai.ops().size(), changes.size());
        if (suggestionLog != null) {
            suggestionLog.suggested(userId, AiFeature.SCHEDULE_EDIT, null, true, ai.ops().size(), changes.size(), null);
        }
        return new ScheduleEditPreviewResponse(summary, changes, overlaps.stream().map(Overlap::text).toList());
    }

    // ───────────────────── 적용 ─────────────────────

    @Transactional
    public ScheduleEditApplyResponse apply(Long userId, List<ScheduleEditOp> ops, LocalDate startDate, LocalDate endDate) {
        Snapshot snap = snapshot(userId, startDate, endDate);
        List<ScheduleEditOp> clean = new ArrayList<>();
        int invalid = 0;
        for (ScheduleEditOp op : ops) {
            ScheduleEditOp c = sanitize(op, snap);
            // 2026-10: 패널에서 고친 값이 지금과 같아졌거나 미리보기 뒤에 일정이 바뀐 변경은 건너뛰고 나머지는 적용해요
            if (c == null) {
                invalid++;
                continue;
            }
            clean.add(c);
        }
        if (clean.isEmpty() && invalid > 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        // 루틴을 바꾸면 그 루틴의 앞으로 Task 를 다시 만들어요 → 같은 루틴의 Task 를 따로 옮기는 변경은 의미가 없어요
        Set<Long> movedRoutines = clean.stream().filter(o -> o.type() == ScheduleEditOpType.MOVE_ROUTINE)
                .map(ScheduleEditOp::targetId).collect(Collectors.toSet());

        int applied = 0;
        List<String> skipped = new ArrayList<>();
        if (invalid > 0) skipped.add(invalid + "개는 지금과 같거나 옮길 수 없는 시간이라 건너뛰었어요");
        List<Long> movedTaskIds = new ArrayList<>();
        List<Long> movedRoutineIds = new ArrayList<>();
        // 루틴 먼저 (Task 를 다시 만드니까) → 그다음 하루짜리 Task
        clean.sort((a, b) -> Boolean.compare(b.type() == ScheduleEditOpType.MOVE_ROUTINE, a.type() == ScheduleEditOpType.MOVE_ROUTINE));
        for (ScheduleEditOp op : clean) {
            if (op.type() == ScheduleEditOpType.MOVE_ROUTINE) {
                applyRoutine(userId, op, snap.routines().get(op.targetId()));
                movedRoutineIds.add(op.targetId());
                applied++;
                continue;
            }
            TimeBlockTaskResponse t = snap.tasks().get(op.targetId());
            if (t.routineId() != null && movedRoutines.contains(t.routineId())) {
                skipped.add("Task '" + t.taskName() + "' — 루틴 시간을 바꿔서 이미 새 시간으로 옮겨졌어요");
                continue;
            }
            LocalDate day = op.date() != null ? op.date() : t.date();
            LocalDateTime start = day.atTime(op.startTime());
            LocalDateTime end = start.plusMinutes(op.durationMinutes() != null ? op.durationMinutes() : taskMinutes(t));
            if (taskScheduleService.tryReschedule(userId, t.taskId(), start, end) != null) {
                movedTaskIds.add(t.taskId());
                applied++;
            } else {
                skipped.add("Task '" + t.taskName() + "' — 그 시간에 직접 정한 다른 일정이 있어요");
            }
        }
        log.info("[ScheduleEdit] 적용 userId={} applied={} skipped={}", userId, applied, skipped.size());
        if (suggestionLog != null) {
            suggestionLog.applied(userId, AiFeature.SCHEDULE_EDIT, null, applied, skipped.size(), null, null,
                    movedTaskIds, movedRoutineIds);
        }
        return new ScheduleEditApplyResponse(applied, skipped);
    }

    private void applyRoutine(Long userId, ScheduleEditOp op, RoutineResponse r) {
        LocalTime start = op.startTime() != null ? op.startTime() : r.getRepeatStartTime();
        int minutes = op.durationMinutes() != null ? op.durationMinutes() : routineMinutes(r);
        boolean timeChanged = start != null && (op.startTime() != null || op.durationMinutes() != null);
        routineService.update(userId, r.getRoutineId(), RoutineUpdateRequest.builder()
                .repeatDays(op.days() != null ? op.days().stream().map(Weekday::toDayOfWeek).toList() : null)
                .repeatStartTime(timeChanged ? start : null)
                .repeatEndTime(timeChanged ? start.plusMinutes(minutes) : null)
                // 시간을 옮기면 요일마다 다르게 정한 시간도 새 시간으로 맞춰요 (안 그러면 그 요일은 그대로 겹쳐요)
                .dayTimes(timeChanged ? List.of() : null)
                .build());
    }

    // ───────────────────── 지금 일정 ─────────────────────

    record Snapshot(LocalDate today, LocalDate from, LocalDate to,
                    Map<Long, RoutineResponse> routines,
                    Map<Long, TimeBlockTaskResponse> tasks,
                    TimetableResponse timetable) {
    }

    Snapshot snapshot(Long userId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 6) {
            throw new CustomException(ErrorCode.TIMETABLE_INVALID_RANGE);
        }
        LocalDate today = clock.today();
        Map<Long, RoutineResponse> routines = new LinkedHashMap<>();
        for (RoutineResponse r : routineReader.getAllRoutinesForDisplay(userId)) {
            if (isLive(r, today)) routines.put(r.getRoutineId(), r);
        }
        TimetableResponse tt = timetableQueryService.getTimetable(userId, from, to);
        Map<Long, TimeBlockTaskResponse> tasks = new LinkedHashMap<>();
        for (TimetableResponse.Day d : tt.days()) {
            for (TimeBlockTaskResponse t : d.tasks()) {
                if (tasks.size() >= MAX_TASKS_IN_CONTEXT) break;
                if (t.plannedStartAt() == null || tasks.containsKey(t.taskId())) continue;
                tasks.put(t.taskId(), t);
            }
        }
        return new Snapshot(today, from, to, routines, tasks, tt);
    }

    static boolean isLive(RoutineResponse r, LocalDate today) {
        boolean status = r.getRoutineStatus() == RoutineStatus.NOT_STARTED || r.getRoutineStatus() == RoutineStatus.IN_PROGRESS;
        return status && (r.getRepeatEndDate() == null || !r.getRepeatEndDate().isBefore(today));
    }

    static boolean movable(TimeBlockTaskResponse t, LocalDate today) {
        return t.taskStatus() == TaskStatus.TODO && t.sourceType() != TaskSourceType.MISSION
                && t.date() != null && !t.date().isBefore(today);
    }

    /** 루틴의 요일별 시간 (그날 0시 기준 분, 자정을 넘기면 end > 1440). 시간이 없는 요일은 빠져요 */
    static Map<DayOfWeek, int[]> slots(RoutineResponse r) {
        Map<DayOfWeek, int[]> out = new EnumMap<>(DayOfWeek.class);
        for (RoutineResponse.RepeatDayInfo d : r.getRepeatDays()) {
            LocalTime s = d.getStartTime() != null ? d.getStartTime() : r.getRepeatStartTime();
            LocalTime e = d.getEndTime() != null ? d.getEndTime() : r.getRepeatEndTime();
            if (s == null || e == null) continue;
            int sm = s.toSecondOfDay() / 60;
            int em = e.toSecondOfDay() / 60;
            if (em <= sm) em += 1440;
            out.put(d.getDayOfWeek(), new int[]{sm, em});
        }
        return out;
    }

    /** 같은 요일 · 시간에 겹치는 루틴 두 개 (요일 · 겹치는 시간) */
    record Overlap(Long a, Long b, String nameA, String nameB, List<DayOfWeek> days, int start, int end) {
        String text() {
            return days.stream().map(d -> DAY_KO[d.getValue() - 1]).collect(Collectors.joining("·"))
                    + " " + hm(start) + "–" + hm(end) + " '" + nameA + "' · '" + nameB + "'";
        }
    }

    static List<Overlap> overlaps(java.util.Collection<RoutineResponse> routines) {
        List<RoutineResponse> list = new ArrayList<>(routines);
        List<Overlap> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Map<DayOfWeek, int[]> si = slots(list.get(i));
            for (int j = i + 1; j < list.size(); j++) {
                Map<DayOfWeek, int[]> sj = slots(list.get(j));
                List<DayOfWeek> days = new ArrayList<>();
                int s = 0, e = 0;
                for (Map.Entry<DayOfWeek, int[]> en : si.entrySet()) {
                    int[] o = sj.get(en.getKey());
                    if (o == null) continue;
                    int[] m = en.getValue();
                    if (m[0] < o[1] && o[0] < m[1]) {
                        days.add(en.getKey());
                        s = Math.max(m[0], o[0]);
                        e = Math.min(m[1], o[1]);
                    }
                }
                if (!days.isEmpty()) {
                    days.sort(null);
                    out.add(new Overlap(list.get(i).getRoutineId(), list.get(j).getRoutineId(),
                            list.get(i).getRoutineName(), list.get(j).getRoutineName(), days, s, e));
                }
            }
        }
        return out;
    }

    private String contextJson(Snapshot snap, List<Overlap> overlaps) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("range", Map.of("from", snap.from().toString(), "to", snap.to().toString()));

        List<Map<String, Object>> routines = new ArrayList<>();
        for (RoutineResponse r : snap.routines().values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getRoutineId());
            m.put("start", r.getRepeatStartTime() != null ? hm(r.getRepeatStartTime()) : null);
            m.put("end", r.getRepeatEndTime() != null ? hm(r.getRepeatEndTime()) : null);
            m.put("goal", r.getGoalCategoryName());
            m.put("name", r.getRoutineName());
            m.put("days", r.getRepeatDays().stream().map(d -> weekday(d.getDayOfWeek()).name()).toList());
            Map<String, String> dayTimes = new LinkedHashMap<>();
            for (RoutineResponse.RepeatDayInfo d : r.getRepeatDays()) {
                if (d.getStartTime() != null && d.getEndTime() != null) {
                    dayTimes.put(weekday(d.getDayOfWeek()).name(), hm(d.getStartTime()) + "-" + hm(d.getEndTime()));
                }
            }
            if (!dayTimes.isEmpty()) m.put("dayTimes", dayTimes);
            if (r.getRepeatEndDate() != null) m.put("until", r.getRepeatEndDate().toString());
            routines.add(m);
        }
        root.put("routines", routines);

        List<Map<String, Object>> tasks = new ArrayList<>();
        for (TimeBlockTaskResponse t : snap.tasks().values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.taskId());
            m.put("goal", t.goalCategoryName());
            m.put("name", t.taskName());
            m.put("date", t.date() != null ? t.date().toString() : null);
            m.put("start", hm(t.plannedStartAt().toLocalTime()));
            m.put("end", t.plannedEndAt() != null ? hm(t.plannedEndAt().toLocalTime()) : null);
            if (t.routineId() != null) m.put("routineId", t.routineId());
            if (t.locked()) m.put("locked", true);
            if (!movable(t, snap.today())) m.put("movable", false);
            tasks.add(m);
        }
        root.put("tasks", tasks);

        List<Map<String, Object>> days = new ArrayList<>();
        for (TimetableResponse.Day d : snap.timetable().days()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d.date().toString());
            if (d.dayRange() != null) {
                m.put("wake", hm(d.dayRange().start().toLocalTime()));
                m.put("bed", hm(d.dayRange().end().toLocalTime()));
            }
            List<String> fixed = d.fixedBlocks().stream()
                    .filter(b -> b.patternType() != LifePatternType.SLEEP)
                    .map(b -> b.title() + " " + hm(b.start().toLocalTime()) + "-" + hm(b.end().toLocalTime()))
                    .toList();
            if (!fixed.isEmpty()) m.put("fixed", fixed);
            days.add(m);
        }
        root.put("days", days);

        root.put("overlaps", overlaps.stream().map(o -> Map.of(
                "routineIds", List.of(o.a(), o.b()),
                "names", List.of(o.nameA(), o.nameB()),
                "days", o.days().stream().map(d -> weekday(d).name()).toList(),
                "time", hm(o.start()) + "-" + hm(o.end()))).toList());
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ───────────────────── 검증 ─────────────────────

    /** AI 가 만든 변경을 다시 검사해요. 쓸 수 없으면 null */
    ScheduleEditOp sanitize(ScheduleEditOp op, Snapshot snap) {
        if (op == null || op.type() == null || op.targetId() == null) return null;
        Integer minutes = op.durationMinutes();
        LocalTime start = op.startTime() != null ? op.startTime().withSecond(0).withNano(0) : null;
        return switch (op.type()) {
            case MOVE_ROUTINE -> {
                RoutineResponse r = snap.routines().get(op.targetId());
                if (r == null) yield null;
                List<Weekday> days = op.days() == null ? null : op.days().stream().filter(Objects::nonNull).distinct().sorted().toList();
                if (days != null && days.isEmpty()) days = null;
                if (minutes != null && (minutes < MIN_MINUTES || minutes > MAX_ROUTINE_MINUTES)) minutes = null;
                // 시간이 없는 루틴은 시작 시각을 같이 정해야 길이를 바꿀 수 있어요
                if (start == null && r.getRepeatStartTime() == null) minutes = null;
                if (days == null && start == null && minutes == null) yield null;
                // 실제로 바뀌는 게 없으면 버려요
                Set<Weekday> current = r.getRepeatDays().stream().map(d -> weekday(d.getDayOfWeek())).collect(Collectors.toSet());
                boolean sameDays = days == null || current.equals(Set.copyOf(days));
                boolean sameStart = start == null || start.equals(r.getRepeatStartTime());
                boolean sameLen = minutes == null || minutes == routineMinutes(r);
                if (sameDays && sameStart && sameLen) yield null;
                yield new ScheduleEditOp(ScheduleEditOpType.MOVE_ROUTINE, r.getRoutineId(),
                        sameDays ? null : days, sameStart ? null : start, sameLen ? null : minutes, null, reasonOf(op));
            }
            case MOVE_TASK -> {
                TimeBlockTaskResponse t = snap.tasks().get(op.targetId());
                if (t == null || !movable(t, snap.today()) || start == null) yield null;
                LocalDate day = op.date() != null ? op.date() : t.date();
                if (day.isBefore(snap.today()) || day.isAfter(snap.today().plusDays(MAX_DAYS_AHEAD))) yield null;
                if (minutes != null && (minutes < MIN_MINUTES || minutes > MAX_TASK_MINUTES)) minutes = null;
                LocalDateTime s = day.atTime(start);
                long len = minutes != null ? minutes : taskMinutes(t);
                if (s.equals(t.plannedStartAt()) && Objects.equals(s.plusMinutes(len), t.plannedEndAt())) yield null;
                // 지금보다 이른 시각으로는 옮기지 않아요
                if (s.isBefore(clock.now().withSecond(0).withNano(0)) && day.equals(snap.today())) yield null;
                yield new ScheduleEditOp(ScheduleEditOpType.MOVE_TASK, t.taskId(), null, start, minutes,
                        day.equals(t.date()) ? null : day, reasonOf(op));
            }
        };
    }

    /** AI 가 적은 이유 (한 문장 · 120자까지) */
    private static String reasonOf(ScheduleEditOp op) {
        String r = op.reason();
        if (r == null || r.isBlank()) return null;
        r = r.strip();
        return r.length() > 120 ? r.substring(0, 120) : r;
    }

    // ───────────────────── 사람이 읽는 전/후 ─────────────────────

    Change describe(ScheduleEditOp op, Snapshot snap) {
        if (op.type() == ScheduleEditOpType.MOVE_ROUTINE) {
            RoutineResponse r = snap.routines().get(op.targetId());
            List<DayOfWeek> days = op.days() != null ? op.days().stream().map(Weekday::toDayOfWeek).toList()
                    : r.getRepeatDays().stream().map(RoutineResponse.RepeatDayInfo::getDayOfWeek).toList();
            LocalTime start = op.startTime() != null ? op.startTime() : r.getRepeatStartTime();
            int minutes = op.durationMinutes() != null ? op.durationMinutes() : routineMinutes(r);
            boolean perDay = r.getRepeatDays().stream().anyMatch(d -> d.getStartTime() != null);
            String before = daysText(r.getRepeatDays().stream().map(RoutineResponse.RepeatDayInfo::getDayOfWeek).toList())
                    + " " + (r.getRepeatStartTime() != null
                    ? hm(r.getRepeatStartTime()) + "–" + hm(r.getRepeatEndTime()) : "시간 자동")
                    + (perDay ? " (요일별 시간 있음)" : "");
            String after = daysText(days) + " " + (start != null ? hm(start) + "–" + hm(start.plusMinutes(minutes)) : "시간 자동")
                    + " · 앞으로 계속";
            return new Change(op, "ROUTINE", "루틴 '" + r.getRoutineName() + "'", r.getGoalCategoryName(),
                    r.getGoalCategoryColorCode(), before, after);
        }
        TimeBlockTaskResponse t = snap.tasks().get(op.targetId());
        LocalDate day = op.date() != null ? op.date() : t.date();
        long minutes = op.durationMinutes() != null ? op.durationMinutes() : taskMinutes(t);
        String before = dayText(t.date()) + " " + hm(t.plannedStartAt().toLocalTime())
                + (t.plannedEndAt() != null ? "–" + hm(t.plannedEndAt().toLocalTime()) : "");
        String after = dayText(day) + " " + hm(op.startTime()) + "–" + hm(op.startTime().plusMinutes(minutes))
                + (t.routineId() != null ? " · 이날만" : "");
        return new Change(op, "TASK", (t.routineId() != null ? "루틴 Task '" : "Task '") + t.taskName() + "'",
                t.goalCategoryName(), t.goalColorCode(), before, after);
    }

    // ───────────────────── 작은 도우미 ─────────────────────

    static int routineMinutes(RoutineResponse r) {
        if (r.getRepeatStartTime() == null || r.getRepeatEndTime() == null) return DEFAULT_ROUTINE_MINUTES;
        long m = Duration.between(r.getRepeatStartTime(), r.getRepeatEndTime()).toMinutes();
        if (m <= 0) m += 1440;
        return (int) m;
    }

    static long taskMinutes(TimeBlockTaskResponse t) {
        if (t.plannedStartAt() != null && t.plannedEndAt() != null) {
            long m = Duration.between(t.plannedStartAt(), t.plannedEndAt()).toMinutes();
            if (m > 0) return m;
        }
        return t.plannedDurationMinutes() != null && t.plannedDurationMinutes() > 0 ? t.plannedDurationMinutes() : 30;
    }

    static Weekday weekday(DayOfWeek d) {
        return Weekday.values()[d.getValue() - 1];
    }

    static String daysText(List<DayOfWeek> days) {
        if (days.size() == 7) return "매일";
        return days.stream().sorted().map(d -> DAY_KO[d.getValue() - 1]).collect(Collectors.joining("·"));
    }

    static String dayText(LocalDate d) {
        return d.getMonthValue() + "/" + d.getDayOfMonth() + "(" + DAY_KO[d.getDayOfWeek().getValue() - 1] + ")";
    }

    static String hm(LocalTime t) {
        return t == null ? "" : String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    static String hm(int minutes) {
        int m = ((minutes % 1440) + 1440) % 1440;
        return String.format("%02d:%02d", m / 60, m % 60);
    }
}
