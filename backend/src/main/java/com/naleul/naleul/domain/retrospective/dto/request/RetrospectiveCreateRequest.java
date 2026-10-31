// retrospective/dto/request/RetrospectiveCreateRequest.java
package com.naleul.naleul.domain.retrospective.dto.request;

import com.naleul.naleul.domain.retrospective.enums.ReviewType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class RetrospectiveCreateRequest {

    @NotNull(message = "회고 타입은 필수입니다.")
    private ReviewType reviewType;

    // null이면 오늘 날짜로 처리 (Service에서)
    private java.time.LocalDate reviewDate;

    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    @NotNull(message = "목표(GoalCategory)는 필수입니다.")
    private Long goalCategoryId;
    private Long generalCategoryId;   // nullable
}