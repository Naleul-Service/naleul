package com.naleul.naleul.domain.goalCategory.dto.request;

import com.naleul.naleul.domain.goalOutcome.enums.OutcomeResult;
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
public class GoalCategoryCompleteRequest {

    @NotNull(message = "종료 날짜는 필수입니다.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate goalCategoryEndDate;

    private String achievement;
    private String achievementImageUrl;

    /** 결과 (선택, V47) — ACHIEVED · PARTIAL · NOT_ACHIEVED. 안 보내면 수치·실천률로 자동 판정 (iOS 기존 버전 호환) */
    private OutcomeResult result;
}