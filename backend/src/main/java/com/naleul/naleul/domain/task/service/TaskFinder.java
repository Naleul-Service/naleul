package com.naleul.naleul.domain.task.service;

import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskFinder {

    private final TaskRepository taskRepository;

    public Task findTaskWithDetails(Long taskId, Long userId) {
        return taskRepository.findByTaskIdAndUserIdWithDetails(taskId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TASK_NOT_FOUND));
    }
}