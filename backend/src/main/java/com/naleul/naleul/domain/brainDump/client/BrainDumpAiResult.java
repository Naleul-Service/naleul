package com.naleul.naleul.domain.brainDump.client;

import com.naleul.naleul.domain.brainDump.enums.DateType;
import com.naleul.naleul.domain.brainDump.enums.GoalMatchType;
import com.naleul.naleul.domain.goalCreation.client.AiCallMeta;
import com.naleul.naleul.domain.task.enums.TaskKind;
import com.naleul.naleul.domain.task.enums.TaskPriority;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * AI 해석 결과 (검증 전 원본). 값이 이상해도 그대로 담고, BrainDumpResultSanitizer 가 최종 판단한다.
 */
public record BrainDumpAiResult(List<Item> items, List<TempGoal> tempGoals, AiCallMeta meta) {

    public record Item(String clientKey, String title, DateType dateType, LocalDate date,
                       LocalTime startTime, LocalTime endTime, Integer estimatedMinutes,
                       TaskPriority priority, TaskKind kind, Goal goal) {
    }

    public record Goal(GoalMatchType type, Long goalCategoryId, Long generalCategoryId, Long milestoneId,
                       String tempKey, String reason) {
    }

    public record TempGoal(String tempKey, String name, String emoji) {
    }
}
