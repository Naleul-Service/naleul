package com.naleul.naleul.domain.task.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record TaskPageResponse(
        List<TaskResponse> tasks,
        int currentPage,
        int totalPages,
        long totalElements,
        boolean hasNext
) {
    public static TaskPageResponse from(Page<TaskResponse> page) {
        return new TaskPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.hasNext()
        );
    }
}