package com.naleul.naleul.domain.work.dto.response;

import com.naleul.naleul.domain.work.entity.WorkTopic;

import java.time.LocalDate;

public record WorkTopicResponse(Long topicId, String name, String emoji, int sortOrder, LocalDate dueDate,
                                boolean completed) {
    public static WorkTopicResponse from(WorkTopic t) {
        return new WorkTopicResponse(t.getWorkTopicId(), t.getTopicName(), t.getEmoji(), t.getSortOrder(),
                t.getDueDate(), t.isMarkedCompleted());
    }
}
