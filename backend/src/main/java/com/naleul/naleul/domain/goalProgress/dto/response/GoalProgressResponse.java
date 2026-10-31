package com.naleul.naleul.domain.goalProgress.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 목표 상세의 "지금 어디쯤?" 도표 데이터.
 *
 * - metric : 수치 목표(체중 72 → 69kg 등)일 때만. 계획선(planLine) · 실제 기록(logs) · 오늘 위치.
 * - tasks  : 모든 목표. 지금까지 해야 했던 Task 대비 완료한 Task (수치가 없는 목표는 이걸로 진행을 보여준다).
 * - reflections : 하루 회고 (예: "마라탕 먹음 · 아쉬워요"). 수치 기록과 같은 날짜끼리 묶어 보여준다.
 * - outcome    : 끝난 목표의 결과 (V47, 아직 안 끝났거나 기록이 없으면 null)
 * - adjustment : 주간 점검 제안 — 2주 연속 실천률이 낮거나 높을 때 루틴 요일 줄이기/늘리기 (없으면 null)
 * - extraMetrics : 사용자가 더 만든 그래프 (V52, 목표 기간 · 직선 계획선). 없으면 빈 목록.
 *                  각 그래프의 expectedValue 가 "오늘은 여기까지 와 있으면 계획대로"인 값이다.
 */
public record GoalProgressResponse(
        Long goalCategoryId,
        LocalDate today,
        LocalDate startDate,
        LocalDate endDate,
        int elapsedDays,
        Integer totalDays,          // 종료일 없으면 null
        MetricProgress metric,      // 수치 목표가 아니면 null
        TaskProgress tasks,
        List<Reflection> reflections, // 최근 회고 (최신순, 최대 30개)
        Outcome outcome,
        Adjustment adjustment,
        List<ExtraMetric> extraMetrics
) {

    /** 추가 그래프 (V52) — metricId 로 기록 · 수정 · 삭제 */
    public record ExtraMetric(Long metricId, int sortOrder, MetricProgress progress) {
    }

    /**
     * 끝난 목표의 결과 — result: ACHIEVED · PARTIAL · NOT_ACHIEVED, source: USER · AUTO
     * metricUnrecorded: 수치 목표인데 수치 기록이 없어 실천률로 판정했다 (화면에서 "수치 기록이 없어 실천률로 판정했어요")
     * autoResult · autoReason · autoMessage (V63): 서버 자동 판정과 그 근거 · 안내 문구
     *   예) "마지막 기록이 목표 수치의 86%예요. 80% 이상이라 자동으로 달성 처리했어요."
     */
    public record Outcome(String result, String source, Integer metricPercent, Integer routineRate, Integer taskRate,
                          boolean metricUnrecorded, String autoResult, String autoReason, String autoMessage) {
    }

    /** 주간 점검 제안 — direction: DOWN 줄이기 · UP 늘리기, day: 빼거나 더할 요일 */
    public record Adjustment(String key, String direction, Long routineId, String routineName,
                             int fromDays, int toDays, String day, int previousWeekRate, int lastWeekRate,
                             String message) {
    }

    /** 하루 회고: 무엇을 했는지 · 어땠는지 */
    public record Reflection(LocalDate date, String mood, String note) {
    }

    public enum Status {
        NOT_STARTED,   // 시작일 전
        AHEAD,         // 계획보다 앞서는 중
        ON_TRACK,      // 계획대로 (오차 ±5% 안)
        BEHIND,        // 계획보다 늦는 중
        ACHIEVED,      // 목표값 도달
        NO_PLAN        // 종료일이 없어 오늘 기대값을 정할 수 없음
    }

    public record MetricProgress(
            String name,
            String unit,
            BigDecimal startValue,
            BigDecimal targetValue,
            BigDecimal currentValue,
            BigDecimal expectedValue,      // 계획대로라면 오늘 값 (NO_PLAN 이면 null)
            BigDecimal progressPercent,    // (현재-시작)/(목표-시작)×100, 소수 1자리. 음수·100 초과 가능
            Status status,
            LocalDate projectedDate,       // 최근 기록 추세대로면 목표 도달 예상일 (추세가 반대거나 기록 부족이면 null)
            LocalDate lastLoggedDate,
            boolean loggedToday,
            List<Point> planLine,          // 시작 → 마일스톤 → 종료일 꺾은선
            List<Log> logs,
            LocalDate targetDate           // 수치 마감일 (계획선 끝, V58). 목표 종료일과 다를 수 있음
    ) {
    }

    public record Point(LocalDate date, BigDecimal value, String label) {
    }

    public record Log(LocalDate date, BigDecimal value, String memo) {
    }

    public record TaskProgress(
            int total,
            int completed,
            int dueByToday,                // 오늘까지 하기로 한 Task 수 (이미 끝낸 Task 포함)
            BigDecimal completionPercent,  // completed / dueByToday × 100 (dueByToday 0 이면 null)
            List<TaskPoint> series         // 날짜별 누적 (기간이 길면 주 단위로 줄임)
    ) {
    }

    /** completed 는 오늘 이후 날짜면 null (아직 모름) */
    public record TaskPoint(LocalDate date, int planned, Integer completed) {
    }
}
