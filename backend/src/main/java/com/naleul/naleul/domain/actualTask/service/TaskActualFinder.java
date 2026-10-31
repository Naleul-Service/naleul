// domain/actualTask/service/TaskActualFinder.java
package com.naleul.naleul.domain.actualTask.service;

import com.naleul.naleul.domain.actualTask.entity.TaskActual;
import com.naleul.naleul.domain.actualTask.repository.TaskActualRepository;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskActualFinder {

    private final TaskActualRepository taskActualRepository;

    public TaskActual findActualWithDetails(Long taskActualId, Long userId) {
        return taskActualRepository.findByTaskActualIdAndUserId(taskActualId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.ACTUAL_TASK_NOT_FOUND));
    }
}