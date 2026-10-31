package com.naleul.naleul.domain.generalCategory.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@Builder  // ← 추가
@AllArgsConstructor // ← 추가
public class GeneralCategoryCompleteRequest {

    @NotNull(message = "종료 날짜는 필수입니다.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate generalCategoryEndDate;

    private String achievement;
    private String achievementImageUrl;
}