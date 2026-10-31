package com.naleul.naleul.domain.activity.service;

import com.naleul.naleul.domain.activity.dto.response.RecordJournalResponse;
import com.naleul.naleul.domain.activity.dto.response.RecordJournalResponse.Day;
import com.naleul.naleul.domain.activity.dto.response.RecordJournalResponse.Item;
import com.naleul.naleul.domain.activity.entity.ActualActivity;
import com.naleul.naleul.domain.activity.repository.ActualActivityRepository;
import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.task.repository.projection.GoalDoneTaskProjection;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 기록형 목표의 업무 일지.
 *
 * 하루 단위로 "완료한 Task"(일반 + 루틴)와 "직접 남긴 기록"을 한 목록으로 모은다.
 * Task 의 날짜·시간은 나의 패턴과 같은 규칙(RecordPatternService.Entry)을 써서, 그래프와 일지가 어긋나지 않게 한다.
 * 한 번에 days 일씩, 오늘부터 과거로 (before 미만).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecordJournalService {

    static final int DEFAULT_DAYS = 14;
    static final int MAX_DAYS = 31;

    private final TaskRepository taskRepository;
    private final ActualActivityRepository activityRepository;
    private final UserFinder userFinder;
    private final CategoryFinder categoryFinder;
    private final PlannerClock clock;

    public RecordJournalResponse get(Long userId, Long goalCategoryId, LocalDate before, Integer days) {
        User user = userFinder.findUser(userId);
        categoryFinder.findGoalCategory(goalCategoryId, user);   // 내 목표인지 확인 (아니면 404)

        LocalDate today = clock.today();
        LocalDate to = before != null && !before.isAfter(today.plusDays(1)) ? before : today.plusDays(1);
        int span = days == null ? DEFAULT_DAYS : Math.min(Math.max(days, 1), MAX_DAYS);
        LocalDate from = to.minusDays(span);

        List<TaskItem> tasks = taskRepository.findCompletedOfGoal(userId, goalCategoryId).stream()
                .map(RecordJournalService::toTaskItem)
                .filter(Objects::nonNull)
                .toList();
        List<ActualActivity> records = activityRepository.findOfGoalBetween(
                userId, goalCategoryId, from.atStartOfDay(), to.atStartOfDay());

        boolean older = tasks.stream().anyMatch(t -> t.date().isBefore(from))
                || activityRepository.existsByUserUserIdAndGoalCategoryGoalCategoryIdAndStartAtBefore(
                userId, goalCategoryId, from.atStartOfDay());

        return build(tasks, records.stream().map(RecordJournalService::toRecordItem).toList(), from, to, older);
    }

    /** 날짜가 정해진 일지 항목 */
    record Dated(LocalDate date, Item item) {
    }

    record TaskItem(LocalDate date, Item item) {
    }

    static TaskItem toTaskItem(GoalDoneTaskProjection t) {
        RecordPatternService.Entry e = RecordPatternService.Entry.of(
                t.getActualStartAt(), t.getActualEndAt(), t.getPlannedStartAt(), t.getPlannedEndAt(),
                t.getPlannedDurationMinutes(), t.getRoutineDate(), t.getScheduledDate(), t.getCompletedAt());
        if (e == null) return null;
        boolean actual = t.getActualStartAt() != null;
        LocalDateTime start = actual ? t.getActualStartAt() : t.getPlannedStartAt();
        LocalDateTime end = actual ? t.getActualEndAt() : t.getPlannedEndAt();
        return new TaskItem(e.date(), new Item("TASK", t.getTaskId(), t.getTaskName(), t.getEmoji(),
                start, end, e.minutes(), t.getRoutineId() != null, null));
    }

    static Dated toRecordItem(ActualActivity a) {
        return new Dated(a.getStartAt().toLocalDate(), new Item("RECORD", a.getActualActivityId(), a.getTitle(),
                a.getEmoji(), a.getStartAt(), a.getEndAt(),
                Math.max(0, Duration.between(a.getStartAt(), a.getEndAt()).toMinutes()), false, a.getMemo()));
    }

    /** [from, to) 안의 항목을 날짜별로 묶는다 (최근 날짜부터, 하루 안에서는 시각순·시각 없는 것은 뒤로) */
    static RecordJournalResponse build(List<TaskItem> tasks, List<Dated> records,
                                       LocalDate from, LocalDate to, boolean older) {
        List<Dated> all = new ArrayList<>();
        tasks.forEach(t -> all.add(new Dated(t.date(), t.item())));
        all.addAll(records);

        Map<LocalDate, List<Item>> byDate = all.stream()
                .filter(d -> !d.date().isBefore(from) && d.date().isBefore(to))
                .collect(Collectors.groupingBy(Dated::date, () -> new TreeMap<>(Comparator.reverseOrder()),
                        Collectors.mapping(Dated::item, Collectors.toList())));

        Comparator<Item> order = Comparator
                .comparing(Item::startAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Item::title, Comparator.nullsLast(Comparator.naturalOrder()));

        List<Day> days = byDate.entrySet().stream().map(e -> {
            List<Item> items = e.getValue().stream().sorted(order).toList();
            int taskCount = (int) items.stream().filter(i -> i.type().equals("TASK")).count();
            long minutes = items.stream().mapToLong(i -> i.minutes() != null ? i.minutes() : 0).sum();
            return new Day(e.getKey(), taskCount, items.size() - taskCount, minutes, items);
        }).toList();

        return new RecordJournalResponse(days, older ? from : null);
    }
}
