package com.naleul.naleul.domain.goalCategory.dto.request;

import com.naleul.naleul.domain.goalCategory.kind.GoalSubType;
import com.naleul.naleul.domain.goalCategory.kind.GoalType;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalCategoryCreateRequest {

    @NotNull(message = "colorId는 필수입니다.")
    private Long colorId;

    @NotBlank(message = "목표 카테고리 이름은 필수입니다.")
    private String goalCategoryName;

    @NotNull(message = "시작 날짜는 필수입니다.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)  // "2024-01-15" 형식 외 값이면 400 에러
    private LocalDate goalCategoryStartDate;

    @NotNull(message = "종료 날짜는 필수입니다.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)  // "2024-01-15" 형식 외 값이면 400 에러
    private LocalDate goalCategoryEndDate;

    private String motive;

    // ── 직접 만들 때 선택 입력 (없으면 null) ──
    private String emoji;
    private String metricName;
    private String metricUnit;
    private BigDecimal startValue;
    private BigDecimal targetValue;

    // ── 목표 카테고리 (명세 6.A.1.2, 선택) — "기타"면 goalKindLabel 1~10자 ──
    private GoalType goalType;
    private GoalSubType goalSubType;
    private String goalKindLabel;
}

