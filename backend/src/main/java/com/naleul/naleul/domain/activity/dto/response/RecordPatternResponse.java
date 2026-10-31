package com.naleul.naleul.domain.activity.dto.response;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * 기록형 목표의 "이 목표에 대한 나의 패턴" — 이 목표의 완료한 Task(일반 + 루틴) 기준.
 *
 * 기록형은 기한·목표 수치가 없어서 달성률(%)을 낼 수 없다.
 * 대신 완료한 Task 가 "언제·얼마나" 쌓였는지와, 최근 4주가 그 전 4주보다 어떻게 달라졌는지(성장)를
 * 숫자로만 보여준다. (유형 이름·분석 문장은 만들지 않는다. 직접 남긴 "한 일" 기록은 여기 섞지 않는다)
 *
 * Task 날짜: 실제 시각 → 계획 시각 → 루틴 날짜 → 권장일 → 완료 버튼 누른 시각.
 * Task 시간: 실제 시각 → 계획 시간. 시간이 없는 Task 는 개수에만 들어간다.
 * 한 주는 월요일 시작.
 */
public record RecordPatternResponse(
        Summary summary,
        Rhythm rhythm,
        When when,
        Routines routines
) {

    // ───────── 요약 ─────────

    public record Summary(
            int totalCount,
            /** 시간을 알 수 있는 Task 의 시간 합 */
            long totalMinutes,
            int activeDays,
            /** 첫 완료 날짜 (없으면 null) */
            LocalDate firstDoneDate,
            /** 최근 4주 (오늘 포함 28일) */
            PeriodStat recent,
            /** 그 전 4주 */
            PeriodStat previous,
            /** 완료한 Task 가 있는 주가 몇 주째 이어지는지. 이번 주가 아직 비었으면 지난주까지 센다 */
            int weekStreak,
            boolean thisWeekDone
    ) {
    }

    public record PeriodStat(LocalDate start, LocalDate end, int count, long minutes, int activeDays) {
    }

    // ───────── 리듬 (잔디 + 주차별) ─────────

    public record Rhythm(
            /** 잔디 시작일 (월요일). 이날부터 오늘까지 */
            LocalDate heatmapStart,
            /** 잔디 칸 — 완료가 있는 날만 */
            List<DayStat> days,
            /** 최근 12주 (오래된 것부터, 빈 주 포함) + 그 주까지의 누적 */
            List<WeekStat> weeks
    ) {
    }

    public record DayStat(LocalDate date, int count, long minutes) {
    }

    public record WeekStat(LocalDate weekStart, int count, long minutes, int cumulativeCount, long cumulativeMinutes) {
    }

    // ───────── 언제 ─────────

    public record When(
            /** 요일 × 시간대 28칸 (월 DAWN, 월 MORNING, …) */
            List<WhenCell> cells,
            /** 시간대별 합계 (DAWN · MORNING · AFTERNOON · EVENING) */
            List<BandStat> bands,
            /** 가장 많이 완료한 칸 (없으면 null) */
            DayOfWeek peakDay,
            String peakBand,
            /** 시각을 몰라서 표에서 뺀 Task 수 (시간 미정으로 완료한 Task) */
            int untimedCount
    ) {
    }

    /** band: DAWN(0–6시) · MORNING(6–12시) · AFTERNOON(12–18시) · EVENING(18–24시) */
    public record WhenCell(DayOfWeek dayOfWeek, String band, int count, long minutes) {
    }

    public record BandStat(String band, int count, long minutes) {
    }

    // ───────── 루틴 ─────────

    /** 목표에 루틴이 없으면 items 가 비어 있다 (화면은 카드를 숨긴다) */
    public record Routines(
            /** 쌓인 시간이 많은 순 (완료가 없는 루틴도 포함) */
            List<RoutineStat> items,
            /** 최근 4주에 그 전 4주보다 눈에 띄게 더 한 루틴 (없으면 null) */
            Long risingRoutineId
    ) {
    }

    /**
     * 완료한 루틴 Task 기준. 시간은 실제 시각 → 계획 시간 → 루틴 기본 시간 순으로 쓴다.
     * recent·previous 는 최근 4주 / 그 전 4주.
     */
    public record RoutineStat(Long routineId, String routineName, String emoji,
                              long minutes, int completedCount,
                              long recentMinutes, int recentCount,
                              long previousMinutes, int previousCount) {
    }
}
