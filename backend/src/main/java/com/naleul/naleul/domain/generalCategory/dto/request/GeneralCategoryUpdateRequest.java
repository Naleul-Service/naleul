package com.naleul.naleul.domain.generalCategory.dto.request;

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
public class GeneralCategoryUpdateRequest {

    private String generalCategoryName;
    private Long goalCategoryId;
    private Long colorId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate generalCategoryStartDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate generalCategoryEndDate;
}
