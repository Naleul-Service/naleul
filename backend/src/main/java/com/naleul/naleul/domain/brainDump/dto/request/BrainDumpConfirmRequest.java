package com.naleul.naleul.domain.brainDump.dto.request;

import com.naleul.naleul.domain.brainDump.enums.DateType;
import com.naleul.naleul.domain.task.enums.TaskKind;
import com.naleul.naleul.domain.task.enums.TaskPriority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 2단계 → 3단계: 확정. 2단계 화면에서 사용자가 고친 최종 값.
 * 각 Task 는 goalCategoryId(기존 목표) 또는 tempGoalKey(이번에 만들 임시 목표) 중 하나가 꼭 있어야 한다.
 */
public record BrainDumpConfirmRequest(
        @NotEmpty @Size(max = 20) List<@Valid Item> items,
        @Size(max = 5) List<@Valid TempGoal> tempGoals,
        /*
         * true: 시간을 정하지 않은 Task 는 저장만 하고 자동 배치하지 않는다 (pending 으로 돌려줌).
         * 화면이 /daily-plans/fill/preview-tasks 로 점선 제안을 받아 "이대로 진행할까요?" 를 물은 뒤 확정한다.
         * null·false: 예전처럼 바로 배치 (구 버전 클라이언트 호환)
         */
        Boolean placeLater
) {
    public BrainDumpConfirmRequest(List<Item> items, List<TempGoal> tempGoals) {
        this(items, tempGoals, null);
    }

    public boolean placeLaterOrFalse() {
        return Boolean.TRUE.equals(placeLater);
    }

    public record Item(
            @NotBlank @Size(max = 40) String clientKey,
            @NotBlank(message = "할 일 이름을 입력해주세요.") @Size(max = 100) String title,
            @NotNull DateType dateType,
            LocalDate scheduledDate,
            LocalDate dueDate,
            LocalTime startTime,
            LocalTime endTime,
            @Min(5) @Max(480) Integer estimatedMinutes,
            TaskPriority priority,
            TaskKind kind,
            Long goalCategoryId,
            Long generalCategoryId,
            Long milestoneId,
            @Size(max = 10) String tempGoalKey,
            /** 업무형 목표의 주제 (WorkTopic). 다른 목표의 주제거나 지워진 주제면 무시 (2026-10) */
            Long workTopicId
    ) {
        /** JSON 은 항상 이 기본 생성자로 읽어요 — 생성자가 둘이라 Jackson 이 헷갈리지 않게 */
        @com.fasterxml.jackson.annotation.JsonCreator
        public Item {
        }

        /** 주제 없이 (예전 클라이언트 · 테스트) */
        public Item(String clientKey, String title, DateType dateType, LocalDate scheduledDate, LocalDate dueDate,
                    LocalTime startTime, LocalTime endTime, Integer estimatedMinutes, TaskPriority priority,
                    TaskKind kind, Long goalCategoryId, Long generalCategoryId, Long milestoneId, String tempGoalKey) {
            this(clientKey, title, dateType, scheduledDate, dueDate, startTime, endTime, estimatedMinutes, priority,
                    kind, goalCategoryId, generalCategoryId, milestoneId, tempGoalKey, null);
        }
    }

    public record TempGoal(
            @NotBlank @Size(max = 10) String tempKey,
            @NotBlank(message = "임시 목표 이름을 입력해주세요.") @Size(max = 30) String name,
            @Size(max = 16) String emoji,
            Long colorId
    ) {
    }
}
