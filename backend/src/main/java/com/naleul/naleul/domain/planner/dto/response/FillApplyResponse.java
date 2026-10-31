package com.naleul.naleul.domain.planner.dto.response;

import java.util.List;

/**
 * 배치 제안 확정 결과.
 * @param placed  확정한 Task 수
 * @param skipped 그사이 다른 일정이 들어왔거나 이미 시간이 정해져서 확정하지 못한 Task id (시간 미정 그대로)
 */
public record FillApplyResponse(int placed, List<Long> skipped) {
}
