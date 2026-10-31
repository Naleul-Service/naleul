package com.naleul.naleul.domain.routine.factory;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.routine.dto.request.RoutineCreateRequest;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routineRepeatDay.entity.RoutineRepeatDay;
import com.naleul.naleul.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RoutineFactoryTest {

    private final RoutineFactory routineFactory = new RoutineFactory();

    @Test
    @DisplayName("request와 연관 엔티티를 받아 Routine을 생성한다")
    void create_buildsRoutineFromRequest() {
        // given
        User user = mock(User.class);
        GoalCategory goalCategory = mock(GoalCategory.class);
        GeneralCategory generalCategory = mock(GeneralCategory.class);

        RoutineCreateRequest request = RoutineCreateRequest.builder()
                .goalCategoryId(1L)
                .generalCategoryId(1L)
                .routineName("아침 운동")
                .repeatStartTime(LocalTime.of(7, 0))
                .repeatEndTime(LocalTime.of(8, 0))
                .repeatStartDate(LocalDate.of(2026, 9, 1))
                .repeatEndDate(LocalDate.of(2026, 9, 30))
                .repeatDays(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
                .notificationEnabled(true)
                .build();

        // when
        Routine routine = routineFactory.create(user, goalCategory, generalCategory, request);

        // then
        assertThat(routine.getUser()).isEqualTo(user);
        assertThat(routine.getGoalCategory()).isEqualTo(goalCategory);
        assertThat(routine.getGeneralCategory()).isEqualTo(generalCategory);
        assertThat(routine.getRoutineName()).isEqualTo("아침 운동");
        assertThat(routine.getRepeatStartTime()).isEqualTo(LocalTime.of(7, 0));
        assertThat(routine.getRepeatEndTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(routine.getRepeatStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(routine.getRepeatEndDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(routine.isNotificationEnabled()).isTrue();
        assertThat(routine.getRepeatDays())
                .extracting(RoutineRepeatDay::getDayOfWeek)
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
    }

    @Test
    @DisplayName("notificationEnabled가 false인 request도 그대로 반영된다")
    void create_withNotificationDisabled_reflectsFalse() {
        // given
        RoutineCreateRequest request = RoutineCreateRequest.builder()
                .goalCategoryId(1L)
                .generalCategoryId(1L)
                .routineName("저녁 운동")
                .repeatStartTime(LocalTime.of(20, 0))
                .repeatEndTime(LocalTime.of(21, 0))
                .repeatStartDate(LocalDate.of(2026, 9, 1))
                .repeatEndDate(LocalDate.of(2026, 9, 30))
                .repeatDays(List.of(DayOfWeek.TUESDAY))
                .notificationEnabled(false)
                .build();

        // when
        Routine routine = routineFactory.create(
                mock(User.class), mock(GoalCategory.class), mock(GeneralCategory.class), request
        );

        // then
        assertThat(routine.isNotificationEnabled()).isFalse();
    }

    @Test
    @DisplayName("repeatDays가 빈 리스트면 Routine의 repeatDays도 빈 리스트가 된다")
    void create_withEmptyRepeatDays_resultsInEmptyRepeatDays() {
        // given
        RoutineCreateRequest request = RoutineCreateRequest.builder()
                .goalCategoryId(1L)
                .generalCategoryId(1L)
                .routineName("아침 운동")
                .repeatStartTime(LocalTime.of(7, 0))
                .repeatEndTime(LocalTime.of(8, 0))
                .repeatStartDate(LocalDate.of(2026, 9, 1))
                .repeatEndDate(LocalDate.of(2026, 9, 30))
                .repeatDays(List.of())
                .notificationEnabled(true)
                .build();

        // when
        Routine routine = routineFactory.create(
                mock(User.class), mock(GoalCategory.class), mock(GeneralCategory.class), request
        );

        // then
        assertThat(routine.getRepeatDays()).isEmpty();
    }
}