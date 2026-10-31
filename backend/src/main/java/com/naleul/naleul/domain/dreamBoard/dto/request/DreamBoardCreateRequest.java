package com.naleul.naleul.domain.dreamBoard.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class DreamBoardCreateRequest {

    @NotNull(message = "목표 카테고리 ID는 필수입니다.")
    private Long goalCategoryId;

    @NotBlank(message = "이미지 URL은 필수입니다.")
    private String imageUrl;
}