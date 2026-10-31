package com.naleul.naleul.domain.goalCreation.personal;

/**
 * 같은 2단계 카테고리(예: 다이어트) 목표를 달성한 사용자들의 익명 집계.
 * 개인 기록 원문은 담지 않는다. 표본이 적으면(AchieverStats.MIN_ACHIEVED 미만) 아예 만들지 않는다.
 *
 * @param categoryLabel             "다이어트"
 * @param achievedCount             달성한 목표 수
 * @param totalCount                끝난 목표 수 (달성 + 부분 + 미달성)
 * @param medianDays                달성자 목표 기간 중앙값(일)
 * @param medianWeeklyRoutineMinutes 달성자 루틴 주간 합계 중앙값(분)
 * @param medianRoutineCount        달성자 루틴 개수 중앙값
 */
public record AchieverSummary(
        String categoryLabel,
        int achievedCount,
        int totalCount,
        int medianDays,
        int medianWeeklyRoutineMinutes,
        int medianRoutineCount
) {
    public int achievedRatePercent() {
        return totalCount == 0 ? 0 : Math.round(100f * achievedCount / totalCount);
    }
}
