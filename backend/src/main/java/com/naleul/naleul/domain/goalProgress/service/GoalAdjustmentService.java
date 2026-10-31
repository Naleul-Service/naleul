package com.naleul.naleul.domain.goalProgress.service;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalCreation.personal.RoutineLoad;
import com.naleul.naleul.domain.goalProgress.service.GoalAdjustmentFinder.Direction;
import com.naleul.naleul.domain.goalProgress.service.GoalAdjustmentFinder.Due;
import com.naleul.naleul.domain.goalProgress.service.GoalAdjustmentFinder.RoutineState;
import com.naleul.naleul.domain.goalProgress.service.GoalAdjustmentFinder.Suggestion;
import com.naleul.naleul.domain.pattern.entity.PatternSuggestionAction;
import com.naleul.naleul.domain.pattern.enums.PatternSuggestionType;
import com.naleul.naleul.domain.pattern.repository.PatternSuggestionActionRepository;
import com.naleul.naleul.domain.planner.repository.TimetableQueryRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.routineRepeatDay.entity.RoutineRepeatDay;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 목표 주간 점검 제안 찾기 · 반영 · 미루기.
 *
 * 반영은 "내일부터"만 바꾼다 (나의 패턴 제안과 같은 방식):
 *  - 줄이기: 루틴 요일에서 하루를 빼고, 그 요일의 내일 이후 미완료 Task 를 지운다
 *  - 늘리기: 루틴 요일에 하루를 더하고, 그 요일의 내일 이후 Task 를 만든다
 * 오늘 이전 Task 와 완료한 Task 는 건드리지 않는다 (지난 기록이 패턴·결과에 그대로 남게).
 * 반영·미루기 기록은 나의 패턴 제안 기록(pattern_suggestion_action)에 LOAD_DOWN / LOAD_UP 으로 같이 남긴다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GoalAdjustmentService {

    /** 루틴 종료일이 없을 때 새 요일 Task 를 미리 만들어 두는 기간 */
    static final int OPEN_ENDED_DAYS = 56;

    private final RoutineRepository routineRepository;
    private final TaskRepository taskRepository;
    private final TimetableQueryRepository taskQueryRepository;
    private final PatternSuggestionActionRepository actionRepository;
    private final CategoryFinder categoryFinder;
    private final UserFinder userFinder;
    private final PlannerClock clock;

    /** 목표 상세 진행 응답에 붙일 제안 (없으면 empty). tasks 는 목표의 Task 전체(이미 불러온 것) */
    @Transactional(readOnly = true)
    public Optional<Suggestion> find(Long userId, GoalCategory goal, List<Task> tasks) {
        if (!goal.isAchievement() || goal.isTemporary() || goal.getGoalCategoryStatus() != GoalCategoryStatus.IN_PROGRESS) {
            return Optional.empty();
        }
        LocalDate today = clock.today();
        List<RoutineState> routines = routineRepository.findAllByGoalCategoryId(goal.getGoalCategoryId()).stream()
                .map(r -> new RoutineState(r.getRoutineId(), r.getRoutineName(), days(r), RoutineLoad.isActive(r, today)))
                .toList();
        List<Due> dues = tasks.stream()
                .filter(t -> t.getRoutine() != null && t.getRoutineDate() != null && t.getRoutineDate().isBefore(today))
                .filter(t -> t.getTaskStatus() != TaskStatus.DELETED)
                .map(t -> new Due(t.getRoutine().getRoutineId(), t.getRoutineDate(), t.getTaskStatus() == TaskStatus.COMPLETED))
                .toList();
        Set<String> blocked = new HashSet<>(actionRepository.findBlockedKeys(userId,
                clock.now().minusDays(PatternSuggestionAction.DISMISS_DAYS)));
        return GoalAdjustmentFinder.find(today, routines, dues, blocked);
    }

    public void apply(Long userId, Long goalCategoryId, String key) {
        GoalCategory goal = ownedGoal(userId, goalCategoryId);
        Suggestion s = current(userId, goal, key);
        Routine routine = routineRepository.findAllByRoutineIdInWithRepeatDays(List.of(s.routineId())).stream()
                .findFirst().orElseThrow(() -> new CustomException(ErrorCode.PATTERN_SUGGESTION_NOT_FOUND));

        Set<DayOfWeek> before = days(routine);
        Set<DayOfWeek> after = EnumSet.copyOf(before);
        LocalDate tomorrow = clock.today().plusDays(1);
        if (s.direction() == Direction.DOWN) {
            after.remove(s.day());
            routine.updateRepeatDays(after.stream().sorted().toList());
            List<Task> removed = taskRepository.findByRoutine(routine).stream()
                    .filter(t -> t.getTaskStatus() == TaskStatus.TODO)
                    .filter(t -> t.getRoutineDate() != null && !t.getRoutineDate().isBefore(tomorrow))
                    .filter(t -> t.getRoutineDate().getDayOfWeek() == s.day())
                    .toList();
            taskRepository.deleteAll(removed);
        } else {
            after.add(s.day());
            routine.updateRepeatDays(after.stream().sorted().toList());
            Set<LocalDate> existing = taskRepository.findByRoutine(routine).stream()
                    .map(Task::getRoutineDate).filter(Objects::nonNull).collect(Collectors.toSet());
            LocalDate from = routine.getRepeatStartDate() != null && routine.getRepeatStartDate().isAfter(tomorrow)
                    ? routine.getRepeatStartDate() : tomorrow;
            LocalDate until = routine.getRepeatEndDate() != null ? routine.getRepeatEndDate() : tomorrow.plusDays(OPEN_ENDED_DAYS);
            List<Task> created = new ArrayList<>();
            for (LocalDate d = from; !d.isAfter(until); d = d.plusDays(1)) {
                if (d.getDayOfWeek() == s.day() && !existing.contains(d)) created.add(Task.fromRoutine(routine, d));
            }
            taskRepository.saveAll(created);
        }
        actionRepository.save(PatternSuggestionAction.applied(userId, s.key(), type(s), routine.getRoutineId(),
                daysJson(before), daysJson(after), clock.now()));
    }

    public void dismiss(Long userId, Long goalCategoryId, String key) {
        GoalCategory goal = ownedGoal(userId, goalCategoryId);
        Suggestion s = current(userId, goal, key);
        actionRepository.save(PatternSuggestionAction.dismissed(userId, s.key(), type(s), s.routineId(), clock.now()));
    }

    /** 화면에 떠 있던 제안이 지금도 같은 제안인지 (그사이 루틴이 바뀌었으면 STALE) */
    private Suggestion current(Long userId, GoalCategory goal, String key) {
        List<Task> tasks = taskQueryRepository.findAllOfGoal(userId, goal.getGoalCategoryId());
        Suggestion s = find(userId, goal, tasks).orElseThrow(() -> new CustomException(ErrorCode.PATTERN_SUGGESTION_STALE));
        if (!s.key().equals(key)) throw new CustomException(ErrorCode.PATTERN_SUGGESTION_STALE);
        return s;
    }

    private GoalCategory ownedGoal(Long userId, Long goalCategoryId) {
        User user = userFinder.findUser(userId);
        return categoryFinder.findGoalCategory(goalCategoryId, user);
    }

    private static PatternSuggestionType type(Suggestion s) {
        return s.direction() == Direction.DOWN ? PatternSuggestionType.LOAD_DOWN : PatternSuggestionType.LOAD_UP;
    }

    private static Set<DayOfWeek> days(Routine r) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (r.getRepeatDays() != null) for (RoutineRepeatDay d : r.getRepeatDays()) days.add(d.getDayOfWeek());
        return days;
    }

    /** {"days":["MONDAY","WEDNESDAY"]} — 나의 패턴 제안 기록과 같은 모양 */
    private static String daysJson(Set<DayOfWeek> days) {
        return days.stream().sorted().map(d -> "\"" + d + "\"").collect(Collectors.joining(",", "{\"days\":[", "]}"));
    }
}
