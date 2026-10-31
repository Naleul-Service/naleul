// retrospective/dto/request/RetrospectiveUpdateRequest.java
package com.naleul.naleul.domain.retrospective.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class RetrospectiveUpdateRequest {

    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    private Long goalCategoryId;      // nullable
    private Long generalCategoryId;   // nullable
}