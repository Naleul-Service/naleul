package com.naleul.naleul.domain.goalProgress.service;

import com.naleul.naleul.domain.goalProgress.service.GoalAdjustmentFinder.Direction;
import com.naleul.naleul.domain.goalProgress.service.GoalAdjustmentFinder.Due;
import com.naleul.naleul.domain.goalProgress.service.GoalAdjustmentFinder.RoutineState;
import com.naleul.naleul.domain.goalProgress.service.GoalAdjustmentFinder.Suggestion;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

import static java.time.DayOfWeek.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 목표 주간 점검 — 2주 연속 낮으면 줄이기, 높으면 늘리기 */
class GoalAdjustmentFinderTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 9);          // 금요일
    static final LocalDate LAST_WEEK = LocalDate.of(2026, 9, 28);      // 지난주 월
    static final LocalDate PREV_WEEK = LocalDate.of(2026, 9, 21);

    private static RoutineState routine(long id, String name, DayOfWeek... days) {
        return new RoutineState(id, name, EnumSet.copyOf(List.of(days)), true);
    }

    /** weeks 주 동안 루틴 요일마다 기록. doneDays 에 든 요일만 완료 */
    private static List<Due> dues(RoutineState r, LocalDate fromWeek, int weeks, Set<DayOfWeek> doneDays) {
        List<Due> list = new ArrayList<>();
        for (int w = 0; w < weeks; w++) {
            for (DayOfWeek d : r.days()) {
                LocalDate date = fromWeek.plusWeeks(w).plusDays(d.getValue() - 1);
                list.add(new Due(r.routineId(), date, doneDays.contains(d)));
            }
        }
        return list;
    }

    @Test
    void 두_주_연속_낮으면_가장_안_되는_요일을_뺀다() {
        RoutineState gym = routine(1L, "헬스", MONDAY, WEDNESDAY, FRIDAY, SATURDAY);
        List<Due> dues = dues(gym, PREV_WEEK.minusWeeks(2), 4, EnumSet.of(WEDNESDAY));   // 수요일만 함 → 25%

        Optional<Suggestion> s = GoalAdjustmentFinder.find(TODAY, List.of(gym), dues, Set.of());
        assertThat(s.isPresent()).isTrue();
        assertThat(s.get().direction()).isEqualTo(Direction.DOWN);
        assertThat(s.get().toDays()).isEqualTo(3);
        assertThat(s.get().day()).isEqualTo(SATURDAY);   // 놓친 요일끼리 같으면 주 뒤쪽
        assertThat(s.get().message().contains("‘헬스’를 주 4회 → 3회로 줄여 볼까요? (토요일 빼기)")).isTrue();
    }

    @Test
    void 두_주_연속_높으면_간격이_가장_넓은_요일을_더한다() {
        RoutineState read = routine(2L, "독서", MONDAY, WEDNESDAY, FRIDAY);
        List<Due> dues = dues(read, PREV_WEEK, 2, EnumSet.of(MONDAY, WEDNESDAY, FRIDAY));   // 100%

        Optional<Suggestion> s = GoalAdjustmentFinder.find(TODAY, List.of(read), dues, Set.of());
        assertThat(s.isPresent()).isTrue();
        assertThat(s.get().direction()).isEqualTo(Direction.UP);
        assertThat(s.get().day()).isEqualTo(SATURDAY);
        assertThat(s.get().message().contains("‘독서’를 주 3회 → 4회로 늘려 볼까요?")).isTrue();
    }

    @Test
    void 한_주만_낮거나_표본이_적으면_제안하지_않는다() {
        RoutineState gym = routine(1L, "헬스", MONDAY, WEDNESDAY, FRIDAY);
        List<Due> dues = new ArrayList<>(dues(gym, PREV_WEEK, 1, EnumSet.of(MONDAY, WEDNESDAY, FRIDAY)));   // 100%
        dues.addAll(dues(gym, LAST_WEEK, 1, EnumSet.noneOf(DayOfWeek.class)));                            // 0%
        assertThat(GoalAdjustmentFinder.find(TODAY, List.of(gym), dues, Set.of()).isPresent()).isFalse();

        RoutineState once = routine(3L, "명상", SUNDAY);
        assertThat(GoalAdjustmentFinder.find(TODAY, List.of(once), dues(once, PREV_WEEK, 2, Set.of()), Set.of()).isPresent()).isFalse();
    }

    @Test
    void 미룬_제안은_다시_내지_않는다() {
        RoutineState read = routine(2L, "독서", MONDAY, WEDNESDAY, FRIDAY);
        List<Due> dues = dues(read, PREV_WEEK, 2, EnumSet.of(MONDAY, WEDNESDAY, FRIDAY));
        String key = GoalAdjustmentFinder.key(2L, Direction.UP, SATURDAY, LAST_WEEK);
        assertThat(GoalAdjustmentFinder.find(TODAY, List.of(read), dues, Set.of(key)).isPresent()).isFalse();
    }
}
