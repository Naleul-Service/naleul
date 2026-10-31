package com.naleul.naleul.domain.autobiography.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class AnswerUpdateRequest {

    @NotBlank(message = "답변 내용은 필수입니다.")
    private String content;
}