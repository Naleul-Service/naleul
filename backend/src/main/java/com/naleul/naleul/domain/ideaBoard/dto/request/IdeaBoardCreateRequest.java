package com.naleul.naleul.domain.ideaBoard.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class IdeaBoardCreateRequest {

    @NotNull(message = "목표 카테고리 ID는 필수입니다.")
    private Long goalCategoryId;  // ⭐ 추가

    @NotBlank(message = "아이디어 내용은 필수입니다.")
    private String idea;

    private String imageUrl;
}