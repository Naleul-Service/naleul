package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanSubGoal;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanTask;
import com.naleul.naleul.domain.goalCreation.dto.plan.TaskType;
import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.enums.RoutineStatus;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.routineRepeatDay.entity.RoutineRepeatDay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 예전 목표(AI 없이 직접 만든 목표)를 AI 로 다시 설계할 때 (2026-10) — 살릴 수 있는 건 살린다.
 *
 * 초안을 만들 때 (merge)
 *  - 지금 하고 있는 루틴을 초안에 그대로 넣는다 (tempId "existing-routine-{id}"). 사용자는 초안 화면에서 고치거나 뺄 수 있다
 *  - 세부 목표가 있으면 AI 가 만든 세부 목표 대신 기존 세부 목표를 쓴다 (tempId "existing-subgoal-{id}")
 *  - AI 가 기존 루틴과 같은 이름의 루틴을 또 만들었으면 AI 쪽을 뺀다
 *
 * 확정할 때는 GoalPlanPersister.persistInto 가 이 tempId 를 보고 새로 만들지 않고 기존 것을 이어서 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyPlanMerger {

    public static final String EXISTING_ROUTINE = "existing-routine-";
    public static final String EXISTING_SUBGOAL = "existing-subgoal-";
    static final Set<RoutineStatus> ACTIVE = Set.of(RoutineStatus.NOT_STARTED, RoutineStatus.IN_PROGRESS);

    private final RoutineRepository routineRepository;
    private final GeneralCategoryRepository generalCategoryRepository;

    public static boolean isExisting(PlanTask t) {
        return t.tempId() != null && t.tempId().startsWith(EXISTING_ROUTINE);
    }

    public static Optional<Long> existingRoutineId(PlanTask t) {
        return idOf(t.tempId(), EXISTING_ROUTINE);
    }

    public static Optional<Long> existingSubGoalId(String tempId) {
        return idOf(tempId, EXISTING_SUBGOAL);
    }

    private static Optional<Long> idOf(String tempId, String prefix) {
        if (tempId == null || !tempId.startsWith(prefix)) return Optional.empty();
        try {
            return Optional.of(Long.parseLong(tempId.substring(prefix.length())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** 확정 전 검증에서는 기존 루틴을 뺀다 (이미 하고 있는 일이라 AI 계획 규칙을 다시 적용하지 않는다) */
    public static GoalPlanDto withoutExisting(GoalPlanDto plan) {
        if (plan == null) return null;
        List<PlanTask> tasks = plan.tasks().stream().filter(t -> !isExisting(t)).toList();
        return new GoalPlanDto(plan.goal(), plan.subGoals(), plan.milestones(), tasks, plan.warnings(), plan.fit());
    }

    /** 지금 하고 있는 루틴 (중지 · 완료 · 삭제 제외) */
    public List<Routine> activeRoutines(GoalCategory goal) {
        return routineRepository.findAllByGoalCategoryId(goal.getGoalCategoryId()).stream()
                .filter(r -> ACTIVE.contains(r.getRoutineStatus()))
                .sorted(Comparator.comparing(Routine::getRoutineId))
                .toList();
    }

    /** 사용자가 만든 세부 목표 (기본 그릇 "기타 할 일" 제외) — 정렬 순서대로 */
    public List<GeneralCategory> subGoals(GoalCategory goal) {
        return generalCategoryRepository.findAllActiveByGoalCategoryId(goal.getGoalCategoryId()).stream()
                .filter(g -> !g.isDefaultBucket())
                .sorted(Comparator.comparingInt(GeneralCategory::getSortOrder)
                        .thenComparing(GeneralCategory::getGeneralCategoryId))
                .toList();
    }

    /** AI 초안에 기존 세부 목표 · 루틴을 넣는다. 예전 목표에서 시작한 세션이 아니면 그대로 */
    public GoalPlanDto merge(GoalCategory source, GoalPlanDto plan) {
        if (source == null || plan == null || !source.isLegacyManual()) return plan;

        // 1) 세부 목표: 기존 것이 있으면 첫 번째를 쓰고, AI 할 일을 모두 그리로 (목표형은 세부 목표 하나만 쓴다 — V52)
        List<GeneralCategory> existingSubGoals = subGoals(source);
        List<PlanSubGoal> subGoals = plan.subGoals();
        String target = subGoals.isEmpty() ? null : subGoals.get(0).tempId();
        if (!existingSubGoals.isEmpty()) {
            GeneralCategory sg = existingSubGoals.get(0);
            target = EXISTING_SUBGOAL + sg.getGeneralCategoryId();
            subGoals = List.of(new PlanSubGoal(target, sg.getGeneralCategoryName(), sg.getEmoji(), sg.getDescription(), null));
        }
        final String sgTempId = target;

        // 2) 루틴: 이미 초안에 있으면(다시 만들기 · 사용자가 고친 초안) 그대로, 없으면 지금 값으로 넣는다
        List<Routine> routines = activeRoutines(source);
        Set<String> inPlan = plan.tasks().stream().map(PlanTask::tempId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> existingNames = routines.stream().map(r -> normalize(r.getRoutineName())).collect(Collectors.toSet());

        List<PlanTask> tasks = new ArrayList<>();
        for (Routine r : routines) {
            String tempId = EXISTING_ROUTINE + r.getRoutineId();
            if (inPlan.contains(tempId)) continue;
            tasks.add(toPlanTask(r, tempId, sgTempId));
        }
        int dropped = 0;
        for (PlanTask t : plan.tasks()) {
            if (!isExisting(t) && t.isRoutine() && existingNames.contains(normalize(t.title()))) {
                dropped++;   // AI 가 같은 루틴을 또 만듦
                continue;
            }
            tasks.add(isExisting(t) || sgTempId == null ? t : withSubGoal(t, sgTempId));
        }
        if (!routines.isEmpty() || dropped > 0) {
            log.info("[GoalDraft] 예전 목표 루틴 살림 goalCategoryId={} routines={} droppedDuplicates={} subGoal={}",
                    source.getGoalCategoryId(), routines.size(), dropped, sgTempId);
        }
        return new GoalPlanDto(plan.goal(), subGoals, plan.milestones(), tasks, plan.warnings(), plan.fit());
    }

    static PlanTask toPlanTask(Routine r, String tempId, String subGoalTempId) {
        List<Weekday> days = r.getRepeatDays().stream()
                .map(RoutineRepeatDay::getDayOfWeek)
                .filter(Objects::nonNull)
                .sorted()
                .map(Weekday::from)
                .toList();
        return new PlanTask(tempId, TaskType.ROUTINE, subGoalTempId, null,
                r.getRoutineName(), r.getEmoji(), durationOf(r), days, r.getRepeatStartTime(),
                r.getRepeatStartDate(), r.getRepeatEndDate(), null, null,
                r.getDescription(), "지금 하고 있는 루틴이라 그대로 이어가요");
    }

    public static Integer durationOf(Routine r) {
        if (r.getDurationMinutes() != null && r.getDurationMinutes() > 0) return r.getDurationMinutes();
        LocalTime s = r.getRepeatStartTime();
        LocalTime e = r.getRepeatEndTime();
        if (s == null || e == null) return 30;
        long m = Duration.between(s, e).toMinutes();
        if (m <= 0) m += 24 * 60;   // 자정을 넘기는 루틴
        return (int) Math.max(5, Math.min(m, 12 * 60));
    }

    private static PlanTask withSubGoal(PlanTask t, String subGoalTempId) {
        return new PlanTask(t.tempId(), t.type(), subGoalTempId, t.milestoneTempId(), t.title(), t.emoji(),
                t.durationMinutes(), t.routineDays(), t.preferredStartTime(), t.startDate(), t.endDate(),
                t.scheduledDate(), t.dueDate(), t.description(), t.reason());
    }

    static String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase();
    }
}
