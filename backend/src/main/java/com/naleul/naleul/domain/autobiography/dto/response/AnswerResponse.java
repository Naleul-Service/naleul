package com.naleul.naleul.domain.autobiography.dto.response;

import com.naleul.naleul.domain.autobiography.entity.AutobiographyAnswer;

import java.time.LocalDateTime;

public record AnswerResponse(
        Long answerId,
        String content,
        LocalDateTime answeredAt,
        LocalDateTime updatedAt
) {
    public static AnswerResponse from(AutobiographyAnswer answer) {
        return new AnswerResponse(
                answer.getAnswerId(),
                answer.getContent(),
                answer.getAnsweredAt(),
                answer.getUpdatedAt()
        );
    }
}