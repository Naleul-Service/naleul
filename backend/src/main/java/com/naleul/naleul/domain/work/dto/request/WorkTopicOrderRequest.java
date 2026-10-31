package com.naleul.naleul.domain.work.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** 주제 순서 — 왼쪽(위)부터의 topicId 순서. 빠진 주제는 그 뒤에 원래 순서대로 */
public record WorkTopicOrderRequest(
        @NotEmpty List<Long> topicIds
) {
}
