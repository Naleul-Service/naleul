package com.naleul.naleul.domain.routine.factory;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RoutineTaskFactoryTest {

    private final RoutineTaskFactory routineTaskFactory = new RoutineTaskFactory();

    // ── 공통 픽스처 헬퍼 ──────────────────────────────────

    private Routine routineWithRepeatDays(LocalDate startDate, LocalDate endDate, List<DayOfWeek> repeatDays) {
        return Routine.create(
                mock(User.class), mock(GoalCategory.class), mock(GeneralCategory.class),
                "테스트 루틴",
                LocalTime.of(7, 0), LocalTime.of(8, 0),
                startDate, endDate,
                repeatDays,
                true
        );
    }

    // ── generateTasks(routine) — excludeDates 없는 단건 오버로드 ──

    @Nested
    @DisplayName("generateTasks(routine)")
    class GenerateTasksWithoutExcludeDates {

        @Test
        @DisplayName("반복요일이 비어있으면 기간 내 모든 날짜에 Task가 생성된다")
        void generateTasks_emptyRepeatDays_generatesEveryDay() {
            // given: 2026-09-01 ~ 2026-09-05, 반복요일 없음(매일 반복 취급)
            Routine routine = routineWithRepeatDays(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), List.of()
            );

            // when
            List<Task> tasks = routineTaskFactory.generateTasks(routine);

            // then: 5일치 전부 생성
            assertThat(tasks).hasSize(5);
            assertThat(tasks)
                    .extracting(Task::getRoutineDate)
                    .containsExactly(
                            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 3),
                            LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 5)
                    );
        }

        @Test
        @DisplayName("반복요일이 지정되면 해당 요일에만 Task가 생성된다")
        void generateTasks_withRepeatDays_generatesOnlyMatchingDays() {
            // given: 2026-09-01(화) ~ 2026-09-07(월), 월/수만 반복
            Routine routine = routineWithRepeatDays(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7),
                    List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
            );

            // when
            List<Task> tasks = routineTaskFactory.generateTasks(routine);

            // then: 이 기간의 수(9/2), 월(9/7) 두 건만 생성
            assertThat(tasks)
                    .extracting(Task::getRoutineDate)
                    .containsExactlyInAnyOrder(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 7));
        }

        @Test
        @DisplayName("시작일과 종료일이 같으면 그 하루에 대해서만 판단한다")
        void generateTasks_sameStartAndEndDate_generatesSingleDayIfMatched() {
            // given: 2026-09-01(화) 하루만, 화요일 반복
            Routine routine = routineWithRepeatDays(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1),
                    List.of(DayOfWeek.TUESDAY)
            );

            // when
            List<Task> tasks = routineTaskFactory.generateTasks(routine);

            // then
            assertThat(tasks)
                    .extracting(Task::getRoutineDate)
                    .containsExactly(LocalDate.of(2026, 9, 1));
        }

        @Test
        @DisplayName("시작일과 종료일이 같고 반복요일이 다르면 Task가 생성되지 않는다")
        void generateTasks_sameStartAndEndDate_generatesNothingIfNotMatched() {
            // given: 2026-09-01(화) 하루만, 월요일만 반복
            Routine routine = routineWithRepeatDays(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1),
                    List.of(DayOfWeek.MONDAY)
            );

            // when
            List<Task> tasks = routineTaskFactory.generateTasks(routine);

            // then
            assertThat(tasks).isEmpty();
        }

        @Test
        @DisplayName("어떤 요일과도 매치되지 않으면 빈 리스트를 반환한다")
        void generateTasks_noMatchingDays_returnsEmptyList() {
            // given: 2026-09-01(화) ~ 2026-09-01(화) 단 하루, 반복요일은 일요일뿐
            Routine routine = routineWithRepeatDays(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1),
                    List.of(DayOfWeek.SUNDAY)
            );

            // when
            List<Task> tasks = routineTaskFactory.generateTasks(routine);

            // then
            assertThat(tasks).isEmpty();
        }
    }

    // ── generateTasks(routine, excludeDates) — 제외 날짜 있는 오버로드 ──

    @Nested
    @DisplayName("generateTasks(routine, excludeDates)")
    class GenerateTasksWithExcludeDates {

        @Test
        @DisplayName("Task를 만들 때 제외할 날짜들의 집합(excludeDates)에 포함된 날짜는 Task가 생성되지 않는다")
        void generateTasks_withExcludeDates_skipsExcludedDates() {
            // given: 2026-09-01 ~ 2026-09-05, 매일 반복, 9/3만 제외
            Routine routine = routineWithRepeatDays(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), List.of()
            );
            Set<LocalDate> excludeDates = Set.of(LocalDate.of(2026, 9, 3));

            // when
            List<Task> tasks = routineTaskFactory.generateTasks(routine, excludeDates);

            // then
            assertThat(tasks)
                    .hasSize(4)
                    .extracting(Task::getRoutineDate)
                    .doesNotContain(LocalDate.of(2026, 9, 3));
        }

        @Test
        @DisplayName("Task를 만들 때 제외할 날짜들의 집합(excludeDates)이 빈 Set이면 모든 반복 날짜에 Task가 생성된다 (기존 동작과 동일)")
        void generateTasks_withEmptyExcludeDates_matchesDefaultBehavior() {
            // given
            Routine routine = routineWithRepeatDays(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), List.of()
            );

            // when
            List<Task> tasks = routineTaskFactory.generateTasks(routine, Set.of());

            // then
            assertThat(tasks).hasSize(5);
        }

        @Test
        @DisplayName("Task를 만들 때 제외할 날짜들의 집합(excludeDates)이 반복요일과 무관한 날짜여도 정상 동작한다 (아무 영향 없음)")
        void generateTasks_excludeDatesNotMatchingRepeatDay_hasNoEffect() {
            // given: 월/수만 반복하는 루틴인데, 제외 날짜로 화요일(애초에 생성 안 될 날짜)을 넘김
            Routine routine = routineWithRepeatDays(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7),
                    List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
            );
            Set<LocalDate> excludeDates = Set.of(LocalDate.of(2026, 9, 1)); // 화요일

            // when
            List<Task> tasks = routineTaskFactory.generateTasks(routine, excludeDates);

            // then: 원래 생성됐어야 할 수/월 2건 그대로 생성됨
            assertThat(tasks)
                    .extracting(Task::getRoutineDate)
                    .containsExactlyInAnyOrder(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 7));
        }

        @Test
        @DisplayName("Task를 만들 때 제외할 날짜들의 집합(excludeDates)이 전체 반복 날짜를 다 덮으면 빈 리스트를 반환한다")
        void generateTasks_excludeDatesCoveringAll_returnsEmptyList() {
            // given: 2026-09-01(화) 하루만, 화요일 반복인데 그 날짜를 제외 처리
            Routine routine = routineWithRepeatDays(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1),
                    List.of(DayOfWeek.TUESDAY)
            );
            Set<LocalDate> excludeDates = Set.of(LocalDate.of(2026, 9, 1));

            // when
            List<Task> tasks = routineTaskFactory.generateTasks(routine, excludeDates);

            // then
            assertThat(tasks).isEmpty();
        }
    }
}