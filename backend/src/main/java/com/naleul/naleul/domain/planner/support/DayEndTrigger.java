package com.naleul.naleul.domain.planner.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * "오늘 일정이 다 끝난 시각" 계산 — 이 시각이 지나면 남은 일을 내일로 넘기고 내일 TimeTable 을 짠다.
 *
 * 예전에는 모든 사용자를 22시에 한 번에 처리해서, 20~23시처럼 아직 하고 있는 일정까지 넘어가는 문제가 있었다.
 * 이제 사용자마다 시각이 다르다:
 *  - 오늘 할 일을 모두 끝냈다 → 바로 (단, earliest 이후)
 *  - 시간이 정해진 일정이 있다 → 마지막 일정이 끝난 뒤 grace 분 (earliest ~ latest 사이로 맞춤)
 *  - 시간 정해진 일정이 없다 → fallback (기본 22시)
 * latest(기본 23:50) 가 지나면 무조건 실행 — 자정을 넘기는 일정이 있어도 내일 계획은 만들어야 하므로.
 */
public record DayEndTrigger(LocalTime earliest, LocalTime fallback, LocalTime latest, int graceMinutes) {

    /**
     * grace 60분 (2026-10, 예전 10분): 마지막 일정이 21시에 끝나도 아직 그 일을 하고 있을 수 있어서,
     * 끝나자마자 남은 일을 내일로 넘기면 "하던 일이 갑자기 사라진다". 1시간은 기다린다.
     */
    public static final DayEndTrigger DEFAULT =
            new DayEndTrigger(LocalTime.of(18, 0), LocalTime.of(22, 0), LocalTime.of(23, 50), 60);

    /**
     * @param lastPlannedEnd 오늘 시작하는 블록 중 가장 늦게 끝나는 시각 (없으면 null)
     * @param taskCount      오늘 Task 개수
     * @param todoCount      그중 아직 안 끝낸 개수
     */
    public LocalDateTime triggerAt(LocalDate today, LocalDateTime lastPlannedEnd, long taskCount, long todoCount) {
        LocalDateTime min = today.atTime(earliest);
        LocalDateTime max = today.atTime(latest);
        LocalDateTime at;
        if (taskCount > 0 && todoCount == 0) {
            at = min; // 다 끝냈으면 기다릴 필요 없음
        } else if (lastPlannedEnd != null) {
            at = lastPlannedEnd.plusMinutes(graceMinutes);
        } else {
            at = today.atTime(fallback);
        }
        if (at.isBefore(min)) at = min;
        if (at.isAfter(max)) at = max;
        return at;
    }
}
