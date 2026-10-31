package com.naleul.naleul.domain.planner.dto.response;

import java.util.List;

/**
 * 여러 Task 를 한 번에 옮긴 결과 (묶음 드래그).
 * tasks              : 옮긴 Task 들 (요청 순서)
 * moved / unscheduled / coveredFixedBlocks : TaskScheduleResponse 와 같은 뜻, 묶음 전체를 합친 것
 */
public record TaskBatchScheduleResponse(
        List<TimeBlockTaskResponse> tasks,
        List<TaskScheduleResponse.Moved> moved,
        List<Long> unscheduled,
        List<TaskScheduleResponse.CoveredFixed> coveredFixedBlocks
) {
}
