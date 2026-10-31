package com.naleul.naleul.domain.autobiography.dto.response;

import com.naleul.naleul.domain.autobiography.entity.AutobiographyQuestion;
import com.naleul.naleul.domain.autobiography.enums.QuestionType;

import java.time.LocalDateTime;
import java.util.List;

public record QuestionWithAnswerResponse(
        Long questionId,
        String content,
        QuestionType questionType,
        LocalDateTime createdAt,
        List<AnswerResponse> answers
) {
    public static QuestionWithAnswerResponse from(AutobiographyQuestion question) {
        return new QuestionWithAnswerResponse(
                question.getQuestionId(),
                question.getContent(),
                question.getQuestionType(),
                question.getCreatedAt(),
                question.getAnswers().stream().map(AnswerResponse::from).toList()
        );
    }
}