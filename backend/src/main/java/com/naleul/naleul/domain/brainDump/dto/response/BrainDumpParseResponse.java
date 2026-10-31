package com.naleul.naleul.domain.brainDump.dto.response;

import com.naleul.naleul.domain.brainDump.enums.DateType;
import com.naleul.naleul.domain.brainDump.enums.GoalMatchType;
import com.naleul.naleul.domain.task.enums.TaskKind;
import com.naleul.naleul.domain.task.enums.TaskPriority;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 2단계 "목표 연결" 화면 데이터.
 * aiFailed = true 면 AI 없이 규칙으로만 해석한 결과 (목표 연결이 비어 있을 수 있음 → 사용자가 고른다)
 * aiLimited = true 면 오늘 AI 해석 횟수를 다 써서 규칙으로만 해석했다 (aiFailed 도 true)
 */
public record BrainDumpParseResponse(List<Item> items, List<TempGoal> tempGoals, boolean aiFailed, boolean aiLimited) {

    public BrainDumpParseResponse(List<Item> items, List<TempGoal> tempGoals, boolean aiFailed) {
        this(items, tempGoals, aiFailed, false);
    }

    public BrainDumpParseResponse limited() {
        return new BrainDumpParseResponse(items, tempGoals, true, true);
    }

    /**
     * dateType=ON  → scheduledDate (그날 할 일)
     * dateType=DUE → dueDate (그날까지) — 실행일은 확정 때 배치 엔진이 고른다
     */
    public record Item(String clientKey, String title, DateType dateType, LocalDate scheduledDate, LocalDate dueDate,
                       LocalTime startTime, LocalTime endTime, int estimatedMinutes,
                       TaskPriority priority, TaskKind kind, GoalLink goal) {
    }

    /** type=NONE 이면 나머지 값이 비어 있다 */
    public record GoalLink(GoalMatchType type,
                           Long goalCategoryId, String goalName, String goalEmoji, String goalColorCode, boolean temporaryGoal,
                           Long generalCategoryId, String generalCategoryName,
                           Long milestoneId, String milestoneTitle,
                           String tempKey, String reason) {
        public static GoalLink none() {
            return new GoalLink(GoalMatchType.NONE, null, null, null, null, false, null, null, null, null, null, null);
        }
    }

    public record TempGoal(String tempKey, String name, String emoji) {
    }
}
