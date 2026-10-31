package com.naleul.naleul.domain.routine.deleter;

import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RoutineCascadeDeleter {

    private final TaskRepository taskRepository;

    /**
     * 루틴을 소프트 삭제하고, 하위 미완료(TODO) Task를 함께 정리한다.
     * 완료된(COMPLETED) Task는 과거 실행 기록 보존을 위해 삭제하지 않는다.
     */
    public void deleteRoutineAndTasks(Routine routine) {
        routine.delete();

        List<Task> incompleteTasks = taskRepository.findByRoutine(routine).stream()
                .filter(task -> task.getTaskStatus() != TaskStatus.COMPLETED)
                .toList();
        taskRepository.deleteAll(incompleteTasks);
    }
}