package com.naleul.naleul.domain.generalCategory.dto.request;

import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralCategoryCreateRequest {

    @NotBlank(message = "카테고리 이름은 필수입니다.")
    private String generalCategoryName;

    @NotNull(message = "목표 카테고리 ID는 필수입니다.")
    private Long goalCategoryId;

    @NotNull(message = "시작 날짜는 필수입니다.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate generalCategoryStartDate;

    @NotNull(message = "종료 날짜는 필수입니다.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate generalCategoryEndDate;

    @NotNull(message = "colorId는 필수입니다.")
    private Long colorId;
}