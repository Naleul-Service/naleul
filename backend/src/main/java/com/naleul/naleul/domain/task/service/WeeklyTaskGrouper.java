package com.naleul.naleul.domain.task.service;

import com.naleul.naleul.domain.task.dto.response.TaskResponse;
import com.naleul.naleul.domain.task.entity.Task;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.naleul.naleul.global.util.KstDateTimeUtils.toDate;

/**
 * getWeeklyTasks 전용 헬퍼.
 * 주어진 Task 목록을 요일(MONDAY~SUNDAY)별로 묶어서 반환한다.
 */
final class WeeklyTaskGrouper {

    private static final List<String> DAY_ORDER = List.of(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
    );

    private WeeklyTaskGrouper() {}

    static Map<String, List<TaskResponse>> groupByDay(List<Task> tasks, LocalDate startDate) {
        Map<String, LocalDate> dayToDate = buildDayToDateMap(startDate);

        Map<String, List<TaskResponse>> tasksByDay = new LinkedHashMap<>();
        DAY_ORDER.forEach(day -> {
            LocalDate dayDate = dayToDate.get(day);
            List<TaskResponse> dayTasks = tasks.stream()
                    .filter(task -> matchesDay(task, dayDate))
                    .map(TaskResponse::from)
                    .toList();
            tasksByDay.put(day, dayTasks);
        });

        return tasksByDay;
    }

    private static Map<String, LocalDate> buildDayToDateMap(LocalDate startDate) {
        Map<String, LocalDate> dayToDate = new LinkedHashMap<>();
        if (startDate != null) {
            for (int i = 0; i < DAY_ORDER.size(); i++) {
                dayToDate.put(DAY_ORDER.get(i), startDate.plusDays(i));
            }
        }
        return dayToDate;
    }

    private static boolean matchesDay(Task task, LocalDate dayDate) {
        if (dayDate == null) return false;

        if (task.getPlannedStartAt() == null) {
            return dayDate.equals(task.getRoutineDate());
        }

        LocalDate startDate = toDate(task.getPlannedStartAt());
        LocalDate endDate = task.getPlannedEndAt() != null
                ? toDate(task.getPlannedEndAt())
                : startDate;

        boolean endsAtMidnight = task.getPlannedEndAt() != null &&
                task.getPlannedEndAt().toLocalTime().equals(LocalTime.MIDNIGHT);

        return startDate.equals(dayDate) || (!endDate.equals(startDate) && !endsAtMidnight && endDate.equals(dayDate));
    }
}