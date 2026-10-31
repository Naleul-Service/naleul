package com.naleul.naleul.domain.planner.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 드래그 결과.
 * moved              : 옮긴 블록에 밀려 다시 배치된 자동 배치 블록 (from → to)
 * unscheduled        : 밀려났는데 들어갈 자리가 없어 "시간 미정"이 된 Task
 * coveredFixedBlocks : 이 Task 가 덮은 고정 시간 (예: 저녁)
 */
public record TaskScheduleResponse(
        TimeBlockTaskResponse task,
        List<Moved> moved,
        List<Long> unscheduled,
        List<CoveredFixed> coveredFixedBlocks
) {
    public record Moved(Long taskId, LocalDateTime fromStart, LocalDateTime toStart, LocalDateTime toEnd, String reason) {
    }

    public record CoveredFixed(Long lifePatternId, String title) {
    }
}
