package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanGoal;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanGoalMetric;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanMilestone;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanSubGoal;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanTask;
import com.naleul.naleul.domain.goalCreation.validator.GoalPlanValidator;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * AI 가 만든 계획에서 "서버가 기계적으로 고칠 수 있는" 실수를 AI 를 다시 부르지 않고 바로잡는다.
 *
 * 왜 필요한가:
 *  AI 에 수정 요청은 1번만 한다. 그런데 AI(특히 Haiku)는 한 곳을 고치면 다른 곳을 깨뜨리는 경우가 많아서
 *  (예: 루틴 개수를 줄이며 마일스톤 날짜 순서가 뒤섞임) 수정 후에도 HARD 위반이 남아 초안 전체가 실패했다.
 *  아래 항목은 정답이 하나뿐이라 서버가 고치는 게 더 빠르고 확실하다.
 *
 *  1. 마일스톤 날짜 순서   → 날짜순 정렬, 같은 날짜가 겹치면 하나만 남기고 연결된 할 일은 남은 쪽으로 옮김
 *  2. 같은 활동 루틴 중복  → 첫 번째 루틴만 남김 ("데이터 분석 강의 (1)", "(2)" 같은 것)
 *  3. 개수 상한 초과       → 루틴 10개, 일회성 30개, 세부 목표 5개까지만 남김 (사용자 한도)
 *                           지운 세부 목표에 연결된 할 일은 마지막으로 남은 세부 목표로 옮김
 *  4. 마지막 마일스톤      → 목표 종료일 이후 마일스톤은 합치고, 마지막 날짜 = 목표 종료일
 *  5. 마일스톤 수치        → 시작값→목표값 방향으로만 움직이게 맞추고, 비어 있으면 균등하게 채움, 마지막 = 목표값
 *  6. 목표 시작일이 과거   → 오늘로 (기간 길이는 유지하지 않고 종료일은 그대로, 종료일이 지나면 그대로 둠)
 *  7. 루틴 기간            → 목표 기간 안으로 자르고, 뒤집혔으면 기간 지정을 지움(= 목표 기간 전체)
 *  8. 일회성 할 일 날짜    → 마감일 없으면 연결 마일스톤 날짜, 마일스톤 없으면 마감일 이후 첫 마일스톤에 연결,
 *                           마감일이 마일스톤보다 늦으면 마일스톤 날짜로, 실행일이 마감일보다 늦으면 실행일 지움
 */
@Slf4j
final class GoalPlanRepairer {

    static final int MAX_ROUTINES = 10;
    static final int MAX_ONE_TIMES = 30;
    static final int MAX_SUB_GOALS = 5;

    private GoalPlanRepairer() {
    }

    static GoalPlanDto repair(GoalPlanDto plan) {
        return repair(plan, null);
    }

    static GoalPlanDto repair(GoalPlanDto plan, LocalDate today) {
        if (plan == null) return null;
        plan = fixGoalStart(plan, today);
        List<PlanMilestone> milestones = plan.milestones() == null ? List.of() : plan.milestones();
        List<PlanTask> tasks = plan.tasks() == null ? List.of() : plan.tasks();

        PlanGoal goal = plan.goal();
        LocalDate goalEnd = goal == null ? null : goal.endDate();

        // 1. 마일스톤 정렬 + 같은 날짜 합치기 + 종료일 이후 마일스톤 합치기
        Map<String, String> milestoneRemap = new HashMap<>();
        List<PlanMilestone> sorted = milestones.stream()
                .sorted(Comparator.comparing(PlanMilestone::dueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<PlanMilestone> fixedMilestones = new ArrayList<>();
        for (PlanMilestone m : sorted) {
            PlanMilestone prev = fixedMilestones.isEmpty() ? null : fixedMilestones.get(fixedMilestones.size() - 1);
            boolean sameDay = prev != null && m.dueDate() != null && Objects.equals(prev.dueDate(), m.dueDate());
            boolean prevAlreadyAtEnd = prev != null && goalEnd != null && prev.dueDate() != null
                    && !prev.dueDate().isBefore(goalEnd);
            if (sameDay || prevAlreadyAtEnd) {
                if (m.tempId() != null && prev.tempId() != null) milestoneRemap.put(m.tempId(), prev.tempId());
                // 뒤쪽(더 늦은) 마일스톤의 수치가 최종값에 가까우니 수치는 뒤쪽 것을 이어받는다
                fixedMilestones.set(fixedMilestones.size() - 1,
                        new PlanMilestone(prev.tempId(), prev.title(), prev.description(), prev.dueDate(),
                                m.targetValue() != null ? m.targetValue() : prev.targetValue()));
                continue;
            }
            fixedMilestones.add(m);
        }
        // 4. 마지막 마일스톤 날짜 = 목표 종료일
        if (!fixedMilestones.isEmpty() && goalEnd != null) {
            int last = fixedMilestones.size() - 1;
            PlanMilestone m = fixedMilestones.get(last);
            boolean afterPrev = last == 0 || fixedMilestones.get(last - 1).dueDate() == null
                    || fixedMilestones.get(last - 1).dueDate().isBefore(goalEnd);
            if (afterPrev && !goalEnd.equals(m.dueDate())) {
                fixedMilestones.set(last, new PlanMilestone(m.tempId(), m.title(), m.description(), goalEnd, m.targetValue()));
            }
        }
        // 5. 마일스톤 수치
        fixedMilestones = fixMilestoneValues(goal, fixedMilestones);

        // 3. 세부 목표 상한
        List<PlanSubGoal> subGoals = plan.subGoals() == null ? List.of() : plan.subGoals();
        Map<String, String> subGoalRemap = new HashMap<>();
        if (subGoals.size() > MAX_SUB_GOALS) {
            String keepTo = subGoals.get(MAX_SUB_GOALS - 1).tempId();
            for (PlanSubGoal sg : subGoals.subList(MAX_SUB_GOALS, subGoals.size())) {
                if (sg.tempId() != null && keepTo != null) subGoalRemap.put(sg.tempId(), keepTo);
            }
            subGoals = subGoals.subList(0, MAX_SUB_GOALS);
        }

        // 2·3. 루틴 중복 제거 + 개수 상한
        Set<String> seenActivities = new HashSet<>();
        List<PlanTask> fixedTasks = new ArrayList<>();
        int routines = 0, oneTimes = 0, dropped = 0;
        for (PlanTask t : tasks) {
            if (t.isRoutine()) {
                String key = t.title() == null ? "" : GoalPlanValidator.activityKey(t.title());
                if ((!key.isEmpty() && !seenActivities.add(key)) || routines >= MAX_ROUTINES) {
                    dropped++;
                    continue;
                }
                routines++;
            } else if (t.isOneTime()) {
                if (oneTimes >= MAX_ONE_TIMES) {
                    dropped++;
                    continue;
                }
                oneTimes++;
            }
            fixedTasks.add(remap(t, milestoneRemap, subGoalRemap));
        }

        // 7·8. 할 일 날짜
        Map<String, PlanMilestone> msById = new HashMap<>();
        for (PlanMilestone m : fixedMilestones) if (m.tempId() != null) msById.put(m.tempId(), m);
        List<PlanMilestone> finalMilestones = fixedMilestones;
        fixedTasks = fixedTasks.stream().map(t -> fixDates(t, goal, finalMilestones, msById)).toList();

        int mergedMilestones = milestones.size() - fixedMilestones.size();
        boolean milestonesChanged = !fixedMilestones.equals(milestones);
        if (dropped > 0 || milestonesChanged || !subGoalRemap.isEmpty()) {
            log.info("[GoalDraft] 계획 자동 보정 droppedTasks={} mergedMilestones={} milestonesChanged={} droppedSubGoals={}",
                    dropped, mergedMilestones, milestonesChanged, subGoalRemap.size());
        }
        return new GoalPlanDto(goal, subGoals, fixedMilestones, fixedTasks, plan.warnings());
    }

    private static GoalPlanDto fixGoalStart(GoalPlanDto plan, LocalDate today) {
        PlanGoal g = plan.goal();
        if (today == null || g == null || g.startDate() == null || !g.startDate().isBefore(today)) return plan;
        if (g.endDate() != null && !g.endDate().isAfter(today)) return plan;   // 고칠 수 없으면 검증에 맡김
        PlanGoal fixed = new PlanGoal(g.title(), g.emoji(), g.category(), g.description(), g.metric(),
                today, g.endDate(), g.planningStyle(), g.aiNote(), g.subType());
        return new GoalPlanDto(fixed, plan.subGoals(), plan.milestones(), plan.tasks(), plan.warnings());
    }

    private static PlanTask fixDates(PlanTask t, PlanGoal goal, List<PlanMilestone> milestones,
                                     Map<String, PlanMilestone> msById) {
        LocalDate gs = goal == null ? null : goal.startDate();
        LocalDate ge = goal == null ? null : goal.endDate();
        if (t.isRoutine()) {
            LocalDate start = t.startDate(), end = t.endDate();
            if (start != null && gs != null && start.isBefore(gs)) start = gs;
            if (end != null && ge != null && end.isAfter(ge)) end = ge;
            if (start != null && end != null && end.isBefore(start)) { start = null; end = null; }
            if (Objects.equals(start, t.startDate()) && Objects.equals(end, t.endDate())) return t;
            return copy(t, t.milestoneTempId(), start, end, t.scheduledDate(), t.dueDate());
        }
        if (!t.isOneTime()) return t;

        String msId = t.milestoneTempId();
        LocalDate due = t.dueDate(), scheduled = t.scheduledDate();
        PlanMilestone ms = msId == null ? null : msById.get(msId);
        if (ms == null && !milestones.isEmpty()) {
            // 마감일 이후 첫 마일스톤, 없으면 마지막 마일스톤
            LocalDate key = due != null ? due : scheduled;
            ms = milestones.stream()
                    .filter(m -> key == null || m.dueDate() == null || !m.dueDate().isBefore(key))
                    .findFirst().orElse(milestones.get(milestones.size() - 1));
            msId = ms.tempId();
        }
        if (due == null) due = ms != null && ms.dueDate() != null ? ms.dueDate() : (scheduled != null ? scheduled : ge);
        if (ms != null && ms.dueDate() != null && due != null && due.isAfter(ms.dueDate())) due = ms.dueDate();
        if (due != null && gs != null && due.isBefore(gs)) due = gs;
        if (scheduled != null && due != null && scheduled.isAfter(due)) scheduled = null;
        if (scheduled != null && gs != null && scheduled.isBefore(gs)) scheduled = null;
        if (Objects.equals(msId, t.milestoneTempId()) && Objects.equals(due, t.dueDate())
                && Objects.equals(scheduled, t.scheduledDate())) return t;
        return copy(t, msId, t.startDate(), t.endDate(), scheduled, due);
    }

    private static PlanTask copy(PlanTask t, String msId, LocalDate start, LocalDate end,
                                 LocalDate scheduled, LocalDate due) {
        return new PlanTask(t.tempId(), t.type(), t.subGoalTempId(), msId, t.title(), t.emoji(),
                t.durationMinutes(), t.routineDays(), t.preferredStartTime(), start, end, scheduled, due,
                t.description(), t.reason());
    }

    private static PlanTask remap(PlanTask t, Map<String, String> milestoneRemap, Map<String, String> subGoalRemap) {
        String ms = t.milestoneTempId() == null ? null : milestoneRemap.get(t.milestoneTempId());
        String sg = t.subGoalTempId() == null ? null : subGoalRemap.get(t.subGoalTempId());
        if (ms == null && sg == null) return t;
        return new PlanTask(t.tempId(), t.type(), sg != null ? sg : t.subGoalTempId(),
                ms != null ? ms : t.milestoneTempId(), t.title(), t.emoji(),
                t.durationMinutes(), t.routineDays(), t.preferredStartTime(), t.startDate(), t.endDate(),
                t.scheduledDate(), t.dueDate(), t.description(), t.reason());
    }

    /**
     * 수치 목표면 마일스톤 수치를 시작값→목표값 사이로, 한 방향으로만 움직이게 맞춘다.
     * 비어 있는 값은 i/n 비율로 채우고, 마지막은 항상 목표값.
     */
    static List<PlanMilestone> fixMilestoneValues(PlanGoal goal, List<PlanMilestone> milestones) {
        PlanGoalMetric metric = goal == null ? null : goal.metric();
        if (metric == null || metric.startValue() == null || metric.targetValue() == null || milestones.isEmpty()) {
            return milestones;
        }
        BigDecimal start = metric.startValue(), target = metric.targetValue();
        int direction = target.compareTo(start);
        int n = milestones.size();
        List<PlanMilestone> out = new ArrayList<>(n);
        BigDecimal prev = start;
        for (int i = 0; i < n; i++) {
            PlanMilestone m = milestones.get(i);
            BigDecimal v;
            if (i == n - 1) {
                v = target;
            } else {
                v = m.targetValue() != null ? m.targetValue()
                        : start.add(target.subtract(start).multiply(BigDecimal.valueOf(i + 1))
                                .divide(BigDecimal.valueOf(n), 1, RoundingMode.HALF_UP));
                if (direction != 0) {
                    if (Integer.signum(v.compareTo(prev)) == -direction) v = prev;       // 거꾸로 가면 직전 값 유지
                    if (Integer.signum(v.compareTo(target)) == direction) v = target;    // 목표 넘으면 목표값
                }
            }
            prev = v;
            out.add(v.equals(m.targetValue()) ? m
                    : new PlanMilestone(m.tempId(), m.title(), m.description(), m.dueDate(), v));
        }
        return out;
    }
}
