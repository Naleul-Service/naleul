package com.naleul.naleul.domain.planner.dto.response;

import com.naleul.naleul.domain.planner.entity.DailyPlan;
import com.naleul.naleul.domain.planner.enums.DailyPlanSource;
import com.naleul.naleul.domain.planner.enums.DailyPlanStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 날짜별 TimeTable 생성 기록. 배너("내일의 TimeTable을 만들었어요")는 needsReview=true 일 때 띄운다.
 * exists=false 면 그날 아직 생성 기록이 없다 (나머지 값은 null/0).
 */
public record DailyPlanResponse(
        LocalDate date,
        boolean exists,
        DailyPlanStatus status,
        DailyPlanSource source,
        String summary,
        boolean needsReview,
        int placedCount,
        int carriedCount,
        int unplacedCount,
        LocalDateTime generatedAt,
        LocalDateTime reviewedAt
) {
    public static DailyPlanResponse from(DailyPlan p) {
        return new DailyPlanResponse(p.getPlanDate(), true, p.getStatus(), p.getSource(), p.getSummary(), p.needsReview(),
                p.getPlacedCount(), p.getCarriedCount(), p.getUnplacedCount(), p.getGeneratedAt(), p.getReviewedAt());
    }

    public static DailyPlanResponse none(LocalDate date) {
        return new DailyPlanResponse(date, false, null, null, null, false, 0, 0, 0, null, null);
    }
}
