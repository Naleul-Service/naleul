package com.naleul.naleul.domain.routine.factory;

import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.task.entity.Task;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class RoutineTaskFactory {

    public List<Task> generateTasks(Routine routine) {
        return generateTasks(routine, Set.of());
    }

    public List<Task> generateTasks(Routine routine, Set<LocalDate> excludeDates) {
        return generateTasks(routine, excludeDates, null);
    }

    /**
     * @param fromDate null 이 아니면 이 날짜(포함)부터만 만든다.
     *                 루틴을 수정할 때 지난 날짜를 다시 만들지 않으려고 쓴다 (지난 놓친 기록 보존).
     */
    public List<Task> generateTasks(Routine routine, Set<LocalDate> excludeDates, LocalDate fromDate) {
        LocalDate current = routine.getRepeatStartDate();
        if (fromDate != null && fromDate.isAfter(current)) {
            current = fromDate;
        }
        LocalDate end = routine.getRepeatEndDate();
        List<Task> tasks = new ArrayList<>();

        while (!current.isAfter(end)) {
            if (isRepeatDay(routine, current) && !excludeDates.contains(current)) {
                tasks.add(Task.fromRoutine(routine, current));
            }
            current = current.plusDays(1);
        }
        return tasks;
    }

    private boolean isRepeatDay(Routine routine, LocalDate date) {
        return routine.getRepeatDays().isEmpty()
                || routine.getRepeatDays().stream()
                .anyMatch(rd -> rd.getDayOfWeek() == date.getDayOfWeek());
    }
}