package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.kind.GoalKind;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.*;
import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.milestone.entity.Milestone;
import com.naleul.naleul.domain.milestone.repository.MilestoneRepository;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.factory.RoutineTaskFactory;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.routine.dto.request.RoutineUpdateRequest;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 검증이 끝난 초안(GoalPlanDto)을 실제 테이블에 저장한다. (A안 매핑)
 *
 *   goal        → goal_category
 *   subGoals    → general_category   (목표와 같은 색, 같은 기간)
 *   milestones  → milestone
 *   ROUTINE     → routine + 날짜별 task (기존 RoutineService.create와 똑같이 RoutineTaskFactory로 즉시 생성)
 *   ONE_TIME    → task (시간 없음, routineDate = 권장 실행일)
 *
 * tempId("sg1", "ms1")는 초안 안에서만 쓰는 이름이라, 저장하면서 실제 엔티티로 바꿔 끼운다(Map).
 *
 * ⚠️ 무료 플랜의 세부 목표 1개 / 루틴 1개 한도는 여기서 검사하지 않는다 (A안 결정:
 *    AI 목표는 목표 개수 한도만 적용). 그래서 RoutineValidator 등 기존 검증기를 거치지 않는다.
 *    트랜잭션은 호출하는 GoalConfirmService가 연다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoalPlanPersister {

    private final GoalCategoryRepository goalCategoryRepository;
    private final GeneralCategoryRepository generalCategoryRepository;
    private final MilestoneRepository milestoneRepository;
    private final RoutineRepository routineRepository;
    private final TaskRepository taskRepository;
    private final RoutineTaskFactory routineTaskFactory;

    /** 예전 목표 AI 설계 — 기존 루틴 살리기 (필드 주입: 기존 테스트 생성자 호환) */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LegacyPlanMerger legacyPlanMerger;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.naleul.naleul.domain.routine.service.RoutineService routineService;

    /**
     * @param motivation       인터뷰에서 받은 동기 원문 (goal_category.motive에 저장)
     * @param deadlineFlexible 사용자가 기한을 말하지 않아 AI가 정한 기간인지
     * @return 저장된 목표
     */
    public GoalCategory persist(User user, UserColor color, GoalPlanDto plan, PlanningStyle planningStyle,
                                String motivation, boolean deadlineFlexible) {
        return persist(user, color, plan, planningStyle, motivation, deadlineFlexible, null);
    }

    /** @param kind 목표 카테고리 (시작 화면에서 고른 값, 없으면 AI 분류값, 그것도 없으면 null) */
    public GoalCategory persist(User user, UserColor color, GoalPlanDto plan, PlanningStyle planningStyle,
                                String motivation, boolean deadlineFlexible, GoalKind kind) {
        PlanGoal g = plan.goal();

        // 1) 목표
        GoalCategory goal = GoalCategory.create(user, color, g.title().strip(), g.startDate(), g.endDate(), motivation);
        PlanGoalMetric m = g.metric();
        goal.applyAiPlan(g.emoji(), g.category(), g.description(), g.aiNote(),
                m != null ? m.name() : null, m != null ? m.unit() : null,
                m != null ? m.startValue() : null, m != null ? m.targetValue() : null,
                deadlineFlexible, planningStyle);
        goal.applyKind(kind);
        goalCategoryRepository.save(goal);

        persistChildren(user, goal, color, plan, 0);
        return goal;
    }

    /**
     * 예전 목표(AI 없이 직접 만든 목표)에 AI 계획을 채운다 (2026-10).
     * 새 목표를 만들지 않으므로 지금 있는 Task · 루틴 · 기록 · 세부 목표 · 노트 · 색상이 모두 그대로 남는다.
     *  - 이름 · 종료일 · 수치 · 설명 · 카테고리는 확정한 계획 값으로
     *  - 시작일은 원래 값 유지 (지금까지의 기록이 기간 밖으로 밀려나지 않게)
     *  - 동기는 인터뷰에서 새로 받았을 때만 바꾼다
     *  - 세부 목표는 기존 세부 목표 뒤에 붙는다
     */
    public GoalCategory persistInto(GoalCategory goal, User user, GoalPlanDto plan, PlanningStyle planningStyle,
                                    String motivation, boolean deadlineFlexible, GoalKind kind) {
        PlanGoal g = plan.goal();
        LocalDate start = goal.getGoalCategoryStartDate() != null ? goal.getGoalCategoryStartDate() : g.startDate();
        goal.update(g.title().strip(), start, g.endDate(), motivation);
        PlanGoalMetric m = g.metric();
        goal.applyAiPlan(g.emoji() != null ? g.emoji() : goal.getEmoji(), g.category(), g.description(), g.aiNote(),
                m != null ? m.name() : null, m != null ? m.unit() : null,
                m != null ? m.startValue() : null, m != null ? m.targetValue() : null,
                deadlineFlexible, planningStyle);
        goal.applyKind(kind);

        int existing = generalCategoryRepository.findAllActiveByGoalCategoryId(goal.getGoalCategoryId()).size();
        persistChildren(user, goal, goal.getUserColor(), plan, existing);
        keepExistingRoutines(user, goal, plan);
        return goal;
    }

    /** "existing-subgoal-{id}" 이고 이 목표의 살아 있는 세부 목표면 그것, 아니면 null (새로 만든다) */
    private GeneralCategory existingSubGoal(GoalCategory goal, String tempId) {
        return LegacyPlanMerger.existingSubGoalId(tempId)
                .flatMap(generalCategoryRepository::findById)
                .filter(gc -> gc.getGoalCategory() != null
                        && gc.getGoalCategory().getGoalCategoryId().equals(goal.getGoalCategoryId()))
                .filter(gc -> gc.getGeneralCategoryStatus() != com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus.DELETED)
                .orElse(null);
    }

    /**
     * 예전 목표의 지금 하고 있는 루틴 (2026-10, 살릴 수 있는 건 살리기)
     *  - 확정한 계획에 남아 있으면: 이름 · 요일 · 시작 시각 · 소요 시간이 바뀐 것만 반영 (지난 기록은 그대로, 오늘부터 다시 만든다)
     *  - 사용자가 초안에서 뺐으면: 오늘까지만 하고 끝낸다 (지난 기록은 남는다)
     */
    private void keepExistingRoutines(User user, GoalCategory goal, GoalPlanDto plan) {
        if (legacyPlanMerger == null || routineService == null) return;
        Map<Long, PlanTask> kept = new HashMap<>();
        plan.tasks().stream().filter(LegacyPlanMerger::isExisting)
                .forEach(t -> LegacyPlanMerger.existingRoutineId(t).ifPresent(id -> kept.put(id, t)));
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        int updated = 0, ended = 0;
        for (Routine r : legacyPlanMerger.activeRoutines(goal)) {
            PlanTask t = kept.get(r.getRoutineId());
            if (t == null) {
                LocalDate end = r.getRepeatStartDate() != null && r.getRepeatStartDate().isAfter(today)
                        ? r.getRepeatStartDate() : today;
                routineService.update(user.getUserId(), r.getRoutineId(),
                        RoutineUpdateRequest.builder().repeatEndDate(end).build());
                ended++;
                continue;
            }
            RoutineUpdateRequest change = changeOf(r, t);
            if (change != null) {
                routineService.update(user.getUserId(), r.getRoutineId(), change);
                updated++;
            }
        }
        log.info("[GoalConfirm] 예전 목표 루틴 goalCategoryId={} kept={} updated={} ended={}",
                goal.getGoalCategoryId(), kept.size(), updated, ended);
    }

    /** 초안에서 바뀐 값만 담은 수정 요청. 바뀐 게 없으면 null */
    static RoutineUpdateRequest changeOf(Routine r, PlanTask t) {
        RoutineUpdateRequest.RoutineUpdateRequestBuilder b = RoutineUpdateRequest.builder();
        boolean changed = false;
        String title = t.title() != null ? t.title().strip() : null;
        if (title != null && !title.isEmpty() && !title.equals(r.getRoutineName())) {
            b.routineName(title);
            changed = true;
        }
        List<DayOfWeek> days = t.routineDays() == null ? List.of()
                : t.routineDays().stream().filter(Objects::nonNull).distinct().map(Weekday::toDayOfWeek).sorted().toList();
        List<DayOfWeek> current = r.getRepeatDays().stream()
                .map(com.naleul.naleul.domain.routineRepeatDay.entity.RoutineRepeatDay::getDayOfWeek)
                .filter(Objects::nonNull).distinct().sorted().toList();
        if (!days.isEmpty() && !days.equals(current)) {
            b.repeatDays(days);
            changed = true;
        }
        java.time.LocalTime start = t.preferredStartTime() != null ? t.preferredStartTime() : r.getRepeatStartTime();
        Integer minutes = t.durationMinutes() != null && t.durationMinutes() > 0
                ? t.durationMinutes() : LegacyPlanMerger.durationOf(r);
        if (start != null && minutes != null) {
            java.time.LocalTime end = start.plusMinutes(minutes);
            if (!start.equals(r.getRepeatStartTime()) || !end.equals(r.getRepeatEndTime())) {
                b.repeatStartTime(start).repeatEndTime(end);
                changed = true;
            }
        }
        return changed ? b.build() : null;
    }

    /** 세부 목표 · 마일스톤 · 할 일 저장. subGoalOffset = 앞에 이미 있는 세부 목표 수 (정렬 순서) */
    private void persistChildren(User user, GoalCategory goal, UserColor color, GoalPlanDto plan, int subGoalOffset) {
        PlanGoal g = plan.goal();

        // 2) 세부 목표
        Map<String, GeneralCategory> subGoalByTempId = new HashMap<>();
        List<PlanSubGoal> subGoals = plan.subGoals();
        for (int i = 0; i < subGoals.size(); i++) {
            PlanSubGoal sg = subGoals.get(i);
            // 예전 목표의 기존 세부 목표는 새로 만들지 않고 그대로 쓴다 (LegacyPlanMerger)
            GeneralCategory reused = existingSubGoal(goal, sg.tempId());
            if (reused != null) {
                subGoalByTempId.put(sg.tempId(), reused);
                continue;
            }
            GeneralCategory general = GeneralCategory.create(sg.title().strip(), user, goal, color, g.startDate(), g.endDate());
            PlanSubGoalMetric sm = sg.metric();
            general.applyAiPlan(sg.emoji(), sg.description(),
                    sm != null ? sm.name() : null, sm != null ? sm.unit() : null,
                    sm != null ? sm.comparator() : null, sm != null ? sm.period() : null,
                    sm != null ? sm.targetValue() : null, subGoalOffset + i);
            generalCategoryRepository.save(general);
            subGoalByTempId.put(sg.tempId(), general);
        }

        // 3) 마일스톤
        Map<String, Milestone> milestoneByTempId = new HashMap<>();
        List<PlanMilestone> milestones = plan.milestones();
        for (int i = 0; i < milestones.size(); i++) {
            PlanMilestone pm = milestones.get(i);
            Milestone milestone = Milestone.create(goal, pm.title().strip(), pm.description(), pm.dueDate(), pm.targetValue(), i);
            milestoneRepository.save(milestone);
            milestoneByTempId.put(pm.tempId(), milestone);
        }

        // 4) 할 일
        boolean notificationAllowed = true;   // BM(2026-10): 푸시 알림은 Free 도 쓸 수 있다
        int routineTaskCount = 0, oneTimeCount = 0;
        for (PlanTask t : plan.tasks()) {
            if (LegacyPlanMerger.isExisting(t)) continue;   // 기존 루틴은 아래 keepExistingRoutines 에서
            GeneralCategory general = subGoalByTempId.get(t.subGoalTempId());
            if (t.isRoutine()) {
                Routine routine = Routine.createFromAi(user, goal, general, t.title().strip(), t.emoji(),
                        t.durationMinutes(), toDayOfWeeks(t.routineDays()), t.preferredStartTime(),
                        t.startDate() != null ? t.startDate() : g.startDate(),
                        t.endDate() != null ? t.endDate() : g.endDate(),   // null = 목표 종료일까지 (명세 4-4)
                        notificationAllowed, t.description());
                Routine saved = routineRepository.save(routine);
                List<Task> routineTasks = routineTaskFactory.generateTasks(saved);
                taskRepository.saveAll(routineTasks);
                routineTaskCount += routineTasks.size();
            } else {
                Task task = Task.createOneTimeFromAi(user, goal, general, milestoneByTempId.get(t.milestoneTempId()),
                        t.title().strip(), t.emoji(), t.durationMinutes(), t.scheduledDate(), t.dueDate());
                taskRepository.save(task);
                oneTimeCount++;
            }
        }

        log.info("[GoalConfirm] 저장 완료 goalCategoryId={} subGoals={} milestones={} routineTasks={} oneTimeTasks={}",
                goal.getGoalCategoryId(), subGoals.size(), milestones.size(), routineTaskCount, oneTimeCount);
    }

    private List<DayOfWeek> toDayOfWeeks(List<Weekday> days) {
        if (days == null) return List.of();
        return days.stream().filter(Objects::nonNull).distinct().map(Weekday::toDayOfWeek).toList();
    }
}
