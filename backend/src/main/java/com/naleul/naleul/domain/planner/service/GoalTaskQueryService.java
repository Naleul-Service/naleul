package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.planner.dto.response.GoalTasksResponse;
import com.naleul.naleul.domain.planner.dto.response.TimeBlockTaskResponse;
import com.naleul.naleul.domain.planner.repository.TimetableQueryRepository;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.user.service.UserFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 목표 상세의 "관련 Task" — 목표에 속한 Task 를 출처(AI·직접 추가·루틴)와 상관없이 한 번에.
 * 정렬 규칙은 GoalTasksResponse 주석 참고.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoalTaskQueryService {

    private final TimetableQueryRepository queryRepository;
    private final TimetableQueryService timetableQueryService;
    private final CategoryFinder categoryFinder;
    private final UserFinder userFinder;
    private final PlannerClock clock;

    public GoalTasksResponse getGoalTasks(Long userId, Long goalCategoryId) {
        // 내 목표인지 확인 (없거나 남의 목표면 404)
        categoryFinder.findGoalCategory(goalCategoryId, userFinder.findUser(userId));

        LocalDateTime now = clock.now();
        LocalDate today = now.toLocalDate();
        List<Task> tasks = queryRepository.findAllOfGoal(userId, goalCategoryId);
        Map<Long, DateTimeRange> actuals = timetableQueryService.actualRanges(tasks);

        List<Row> rows = tasks.stream()
                .map(t -> new Row(t, sortAt(t, actuals.get(t.getTaskId())), actuals.get(t.getTaskId())))
                .sorted(order(today))
                .toList();

        int completed = (int) tasks.stream().filter(t -> t.getTaskStatus() == TaskStatus.COMPLETED).count();
        return new GoalTasksResponse(
                tasks.size(),
                tasks.size() - completed,
                completed,
                rows.stream().map(r -> TimeBlockTaskResponse.from(r.task(), now, r.actual())).toList());
    }

    /** 정렬용 시각: 화면 블록 위치(완료 후 실제 → 계획) → 시간 미정이면 routineDate·권장일·마감일 0시 */
    static LocalDateTime sortAt(Task t, DateTimeRange actual) {
        DateTimeRange r = TimetableQueryService.blockRange(t, actual);
        if (r != null) return r.start();
        LocalDate d = t.getRoutineDate() != null ? t.getRoutineDate()
                : t.getScheduledDate() != null ? t.getScheduledDate()
                : t.getDueDate();
        return d != null ? d.atStartOfDay() : null;
    }

    static Comparator<Row> order(LocalDate today) {
        return Comparator
                // 1) 미완료 먼저
                .comparing((Row r) -> r.done() ? 1 : 0)
                // 2) 날짜 없는 Task 는 묶음 맨 아래
                .thenComparing(r -> r.sortAt() == null ? 1 : 0)
                // 3) 오늘과 가까운 날짜 먼저
                .thenComparingLong(r -> r.sortAt() == null ? 0
                        : Math.abs(ChronoUnit.DAYS.between(today, r.sortAt().toLocalDate())))
                // 4) 거리가 같으면: 미완료는 이른 날짜·시각 먼저(놓친 것 우선), 완료는 최근 것 먼저
                .thenComparing((a, b) -> {
                    if (a.sortAt() == null || b.sortAt() == null) return 0;
                    int c = a.sortAt().compareTo(b.sortAt());
                    return a.done() ? -c : c;
                })
                .thenComparing(r -> r.task().getTaskId());
    }

    record Row(Task task, LocalDateTime sortAt, DateTimeRange actual) {
        boolean done() {
            return task.getTaskStatus() == TaskStatus.COMPLETED;
        }
    }
}
