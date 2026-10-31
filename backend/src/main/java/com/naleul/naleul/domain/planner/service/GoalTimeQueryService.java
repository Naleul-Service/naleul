package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.planner.dto.response.GoalTimeResponse;
import com.naleul.naleul.domain.planner.repository.TimetableQueryRepository;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 목표별 실천 시간 (2026-10) — "이 기간에 어느 목표에 시간을 썼나요?"
 *
 * 규칙
 *  - 완료(COMPLETED)한 Task 만 센다.
 *  - 시간: 나눠서 한 구간 → 실제 구간 → 계획 구간(나눠 배치했으면 그 구간들) → 시간 미정이면 예상 소요 시간 순으로 쓴다.
 *    (실제 시각 없이 완료만 누른 Task 는 "계획한 시각에 했다"고 본다 — ActualRangeReader 와 같은 규칙)
 *  - 기간 [startDate 00:00, endDate+1 00:00) 밖으로 나간 부분은 잘라서 센다.
 *  - 시간 미정 Task 는 routineDate 가 기간 안일 때만.
 *  - 조회 기간은 최대 42일 (월간 달력 6주).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoalTimeQueryService {

    static final int MAX_DAYS = 42;

    private final TimetableQueryRepository queryRepository;
    private final ActualRangeReader actualRangeReader;
    @Autowired(required = false)
    private PlannedSegmentStore plannedSegmentStore;

    public GoalTimeResponse getGoalTime(Long userId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)
                || ChronoUnit.DAYS.between(startDate, endDate) >= MAX_DAYS) {
            throw new CustomException(ErrorCode.GOAL_TIME_INVALID_RANGE);
        }
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.plusDays(1).atStartOfDay();

        Map<Long, Task> done = new LinkedHashMap<>();
        for (Task t : queryRepository.findPlacedInRange(userId, from, to)) putIfDone(done, t);
        for (Task t : queryRepository.findCompletedWithActualInRange(userId, from, to)) putIfDone(done, t);
        for (Task t : queryRepository.findUnscheduledInRange(userId, startDate, endDate)) putIfDone(done, t);

        Map<Long, DateTimeRange> actuals = actualRangeReader.actualRanges(done.values());
        Map<Long, List<DateTimeRange>> segments = actualRangeReader.actualSegments(done.values());
        Map<Long, List<DateTimeRange>> planned = plannedSegmentStore != null
                ? plannedSegmentStore.segmentsOf(done.values()) : Map.of();

        List<Spent> spent = new ArrayList<>();
        for (Task t : done.values()) {
            long minutes = minutesOf(t, actuals.get(t.getTaskId()),
                    segments.getOrDefault(t.getTaskId(), List.of()),
                    planned.getOrDefault(t.getTaskId(), List.of()),
                    from, to, startDate, endDate);
            if (minutes > 0) spent.add(new Spent(t.getGoalCategory(), minutes));
        }
        return summarize(startDate, endDate, spent);
    }

    private static void putIfDone(Map<Long, Task> map, Task t) {
        if (t.getTaskStatus() == TaskStatus.COMPLETED) map.putIfAbsent(t.getTaskId(), t);
    }

    /** 이 Task 가 [from, to) 안에서 쓴 분 */
    static long minutesOf(Task t, DateTimeRange actual, List<DateTimeRange> actualSegs, List<DateTimeRange> plannedSegs,
                          LocalDateTime from, LocalDateTime to, LocalDate startDate, LocalDate endDate) {
        List<DateTimeRange> ranges;
        if (actualSegs.size() >= 2) ranges = actualSegs;
        else if (actual != null) ranges = List.of(actual);
        else if (t.getPlannedStartAt() != null) {
            if (plannedSegs.size() >= 2) ranges = plannedSegs;
            else {
                DateTimeRange r = ActualRangeReader.occupied(t, null, 30);
                ranges = r != null ? List.of(r) : List.of();
            }
        } else {
            // 시간 미정 — 그날(routineDate)이 기간 안이면 예상 소요 시간만큼
            LocalDate d = t.getRoutineDate();
            if (d == null || d.isBefore(startDate) || d.isAfter(endDate)) return 0;
            Long m = t.getPlannedDurationMinutes();
            return m != null && m > 0 ? m : 0;
        }
        long sum = 0;
        for (DateTimeRange r : ranges) {
            LocalDateTime s = r.start().isBefore(from) ? from : r.start();
            LocalDateTime e = r.end().isAfter(to) ? to : r.end();
            if (e.isAfter(s)) sum += ChronoUnit.MINUTES.between(s, e);
        }
        return sum;
    }

    record Spent(GoalCategory goal, long minutes) {}

    static GoalTimeResponse summarize(LocalDate startDate, LocalDate endDate, List<Spent> spent) {
        Map<Long, long[]> byGoal = new LinkedHashMap<>();      // [minutes, count]
        Map<Long, GoalCategory> goals = new HashMap<>();
        long total = 0;
        for (Spent s : spent) {
            Long key = s.goal() != null ? s.goal().getGoalCategoryId() : null;
            long[] acc = byGoal.computeIfAbsent(key, k -> new long[2]);
            acc[0] += s.minutes();
            acc[1] += 1;
            if (s.goal() != null) goals.putIfAbsent(key, s.goal());
            total += s.minutes();
        }
        List<GoalTimeResponse.GoalTime> rows = new ArrayList<>();
        byGoal.forEach((id, acc) -> {
            GoalCategory g = id != null ? goals.get(id) : null;
            rows.add(new GoalTimeResponse.GoalTime(
                    id,
                    g != null ? g.getGoalCategoryName() : null,
                    g != null ? g.getEmoji() : null,
                    g != null && g.getUserColor() != null ? g.getUserColor().getColorCode() : null,
                    acc[0], (int) acc[1]));
        });
        // 많이 쓴 순, 목표 없음은 같은 시간이면 뒤로
        rows.sort(Comparator.comparingLong(GoalTimeResponse.GoalTime::minutes).reversed()
                .thenComparing(r -> r.goalCategoryId() == null));
        return new GoalTimeResponse(startDate, endDate, total, rows);
    }
}
