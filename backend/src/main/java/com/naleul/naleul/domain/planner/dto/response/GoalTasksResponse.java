package com.naleul.naleul.domain.planner.dto.response;

import java.util.List;

/**
 * GET /api/v1/goal-categories/{goalCategoryId}/tasks — 목표에 속한 Task 전체.
 *
 * tasks 정렬 (서버에서 정렬해서 내려줌)
 *  1) 미완료(TODO·SKIPPED) 먼저, 완료(COMPLETED)는 아래
 *  2) 같은 묶음 안에서는 오늘과 가까운 날짜일수록 위 (날짜 없는 Task 는 묶음 맨 아래)
 *     - 미완료: 거리가 같으면 지난 날짜(놓친 것)를 먼저, 같은 날은 이른 시각 먼저
 *     - 완료: 거리가 같으면 최근 날짜 먼저, 같은 날은 늦은 시각 먼저
 *  날짜 = 블록 위치 기준 (완료 후 실제 시각이 있으면 실제 날짜, 아니면 계획 날짜 / 시간 미정은 routineDate)
 */
public record GoalTasksResponse(
        int totalCount,
        int todoCount,
        int completedCount,
        List<TimeBlockTaskResponse> tasks
) {
}
