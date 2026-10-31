package com.naleul.naleul.domain.goalCategory.dto.request;

import com.naleul.naleul.domain.goalCategory.kind.GoalSubType;
import com.naleul.naleul.domain.goalCategory.kind.GoalType;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalCategoryUpdateRequest {
    private Long colorId;
    private String goalCategoryName;
    private String motive;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate goalCategoryStartDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate goalCategoryEndDate;

    // ── 목표 상세 화면에서 추가로 바꿀 수 있는 값 (null 이면 그대로, 문자열은 "" 이면 지움) ──
    private String emoji;
    private String description;
    private String metricName;
    private String metricUnit;
    private BigDecimal startValue;
    private BigDecimal currentValue;
    private BigDecimal targetValue;
    /** 수치 목표 마감일 (V58). 목표 종료일과 따로 — null 이면 그대로 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate metricTargetDate;
    /** true 면 수치 마감일을 지워 다시 목표 종료일을 따르게 한다 */
    private Boolean clearMetricTargetDate;
    /** true 면 수치 목표를 지운다 ("수치 없이 진행하기") — 이름·단위·시작·현재·목표값과 점검 시점 수치까지 */
    private Boolean clearMetric;

    // ── 목표 카테고리 (명세 6.A.1.2, 선택) — "기타"면 goalKindLabel 1~10자 ──
    private GoalType goalType;
    private GoalSubType goalSubType;
    private String goalKindLabel;

    /**
     * V52 에서 업무형 "마감 없이 진행하기"용으로 추가했지만, V53 부터 업무형은 전체 마감 자체가 없어서 쓰이지 않는다.
     * (예전 앱 요청 호환용으로만 남겨 둠 — 값과 상관없이 업무형 종료일은 항상 비운다)
     */
    private Boolean clearEndDate;
}