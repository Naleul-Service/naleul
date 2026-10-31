package com.naleul.naleul.global.util;

import com.naleul.naleul.domain.task.enums.TaskSourceType;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;

import java.time.Duration;
import java.time.LocalDateTime;

public final class TaskCommonUtils {

    private TaskCommonUtils() {}

    public static Long calculateMinutes(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return null;
        return Duration.between(start, end).toMinutes();
    }

    public static String parseSourceType(String sourceType) {
        if (sourceType == null) return null;
        String upper = sourceType.toUpperCase();
        try {
            TaskSourceType.valueOf(upper);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_ENUM_VALUE);
        }
        return upper;
    }
}