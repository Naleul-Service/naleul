package com.naleul.naleul.domain.planner.dto.response;

import com.naleul.naleul.domain.planner.enums.ChangeScope;

import java.time.LocalDate;
import java.util.List;

/**
 * @param result       드래그한 그날 Task 의 결과 (기존 PATCH /schedule 응답과 같음)
 * @param changedDates 같이 옮긴 다른 날짜들
 * @param skippedDates 잠긴 일정과 겹쳐서 건너뛴 날짜들 (그날은 원래 시간 그대로)
 */
public record RoutineScheduleResponse(
        ChangeScope scope,
        TaskScheduleResponse result,
        List<LocalDate> changedDates,
        List<LocalDate> skippedDates
) {
}
