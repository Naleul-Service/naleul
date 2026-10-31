package com.naleul.naleul.domain.autobiography.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class QuestionCreateRequest {

    @NotBlank(message = "질문 내용은 필수입니다.")
    private String content;
}