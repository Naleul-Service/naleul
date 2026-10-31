package com.naleul.naleul.domain.habit.dto.response;

import com.naleul.naleul.domain.habit.enums.HabitDayState;
import com.naleul.naleul.domain.habit.enums.HabitStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * 습관 잔디맵 (깃허브 잔디처럼, V52) — GET /api/v1/habits/heatmap?from=&to=
 *
 * days  : 모든 습관을 합친 하루 — level 0(안 함) ~ 4(해야 하는 습관을 다 함)
 * habits: 습관별 한 줄 — cells[i] 는 from + i 일의 상태 (days 와 같은 길이 · 같은 순서)
 */
public record HabitHeatmapResponse(
        LocalDate from,
        LocalDate to,
        LocalDate today,
        List<Day> days,
        List<HabitRow> habits
) {
    public record Day(LocalDate date, int done, int scheduled, int level) {
    }

    public record HabitRow(Long habitId, String name, String emoji, HabitStatus status,
                           int streak, Integer rate, int doneDays, List<HabitDayState> cells) {
    }
}
