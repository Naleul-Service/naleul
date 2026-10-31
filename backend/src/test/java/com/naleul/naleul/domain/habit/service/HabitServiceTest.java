package com.naleul.naleul.domain.habit.service;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.habit.dto.request.HabitCreateRequest;
import com.naleul.naleul.domain.habit.dto.response.HabitResponse;
import com.naleul.naleul.domain.habit.entity.Habit;
import com.naleul.naleul.domain.habit.entity.HabitCheck;
import com.naleul.naleul.domain.habit.entity.HabitPause;
import com.naleul.naleul.domain.habit.enums.HabitStatus;
import com.naleul.naleul.domain.habit.repository.HabitCheckRepository;
import com.naleul.naleul.domain.habit.repository.HabitPauseRepository;
import com.naleul.naleul.domain.habit.repository.HabitRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HabitServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);
    private static final Long USER_ID = 7L;

    @Mock HabitRepository habitRepository;
    @Mock HabitCheckRepository checkRepository;
    @Mock HabitPauseRepository pauseRepository;
    @Mock HabitGoalProvisioner provisioner;
    @Mock UserFinder userFinder;
    @Mock PlannerClock clock;
    @InjectMocks HabitService service;

    private User user;
    private GoalCategory home;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        when(user.getUserId()).thenReturn(USER_ID);
        home = GoalCategory.createHabitHome(user, null, "기본 생활 습관", "🌱", TODAY);
        ReflectionTestUtils.setField(home, "goalCategoryId", 100L);
        when(userFinder.findUser(USER_ID)).thenReturn(user);
        when(provisioner.ensure(user)).thenReturn(new HabitGoalProvisioner.HabitHome(100L, "기본 생활 습관", "🌱"));
        when(provisioner.reference(any())).thenReturn(home);
        when(clock.today()).thenReturn(TODAY);
        when(checkRepository.findBetween(any(), any(), any())).thenReturn(List.of());
        when(pauseRepository.findAllOf(any())).thenReturn(List.of());
        when(habitRepository.save(any(Habit.class))).thenAnswer(inv -> {
            Habit h = inv.getArgument(0);
            ReflectionTestUtils.setField(h, "habitId", 1L);
            return h;
        });
    }

    private Habit habit(LocalDate start) {
        Habit h = Habit.create(user, home, "영양제 먹기", "💊", List.of(), LocalTime.of(8, 30), 0, start);
        ReflectionTestUtils.setField(h, "habitId", 1L);
        when(habitRepository.findOwned(1L, USER_ID)).thenReturn(Optional.of(h));
        return h;
    }

    @Test
    void 습관을_추가하면_기본_생활_습관_그릇에_들어간다() {
        when(habitRepository.countOfUser(USER_ID)).thenReturn(0L);
        when(habitRepository.findMaxSortOrder(USER_ID)).thenReturn(2);

        HabitResponse r = service.create(USER_ID, new HabitCreateRequest("  샤워하기 ", "🚿",
                List.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), LocalTime.of(22, 0, 33)));

        ArgumentCaptor<Habit> saved = ArgumentCaptor.forClass(Habit.class);
        verify(habitRepository).save(saved.capture());
        assertThat(saved.getValue().getGoalCategory()).isSameAs(home);
        assertThat(saved.getValue().getSortOrder()).isEqualTo(3);
        assertThat(r.name()).isEqualTo("샤워하기");
        assertThat(r.repeatDays()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.THURSDAY);
        assertThat(r.remindTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(r.scheduled()).isFalse();   // 오늘은 화요일
        assertThat(r.recent7()).hasSize(7);
    }

    @Test
    void 습관은_30개까지() {
        when(habitRepository.countOfUser(USER_ID)).thenReturn(30L);
        assertThatThrownBy(() -> service.create(USER_ID, new HabitCreateRequest("물", null, null, null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.HABIT_LIMIT_EXCEEDED);
    }

    @Test
    void 미래_날짜는_체크할_수_없다() {
        habit(TODAY);
        assertThatThrownBy(() -> service.check(USER_ID, 1L, TODAY.plusDays(1)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.HABIT_CHECK_FUTURE_DATE);
    }

    @Test
    void 만든_날보다_전날을_체크하면_시작일을_당기고_INSERT_IGNORE_로_넣는다() {
        Habit h = habit(TODAY);

        service.check(USER_ID, 1L, TODAY.minusDays(1));

        assertThat(h.getStartDate()).isEqualTo(TODAY.minusDays(1));
        verify(checkRepository).insertIgnore(eq(1L), eq(USER_ID), eq(TODAY.minusDays(1)), any());
        verify(checkRepository, never()).save(any());
    }

    @Test
    void 체크하면_응답에_오늘_체크와_연속기록이_보인다() {
        Habit h = habit(TODAY.minusDays(2));
        when(checkRepository.findBetween(any(), any(), any())).thenReturn(List.of(
                HabitCheck.of(h, TODAY), HabitCheck.of(h, TODAY.minusDays(1))));

        HabitResponse r = service.check(USER_ID, 1L, TODAY);

        assertThat(r.checked()).isTrue();
        assertThat(r.streak()).isEqualTo(2);
        assertThat(r.rate30()).isEqualTo(67);   // 3일 중 2일
    }

    @Test
    void 쉬기_다시시작_같은날이면_쉰_기간을_지운다() {
        Habit h = habit(TODAY.minusDays(5));
        service.pause(USER_ID, 1L);
        assertThat(h.getHabitStatus()).isEqualTo(HabitStatus.PAUSED);
        ArgumentCaptor<HabitPause> pause = ArgumentCaptor.forClass(HabitPause.class);
        verify(pauseRepository).save(pause.capture());

        when(pauseRepository.findOpen(1L)).thenReturn(Optional.of(pause.getValue()));
        service.resume(USER_ID, 1L);

        assertThat(h.getHabitStatus()).isEqualTo(HabitStatus.ACTIVE);
        verify(pauseRepository).delete(pause.getValue());
    }

    @Test
    void 없는_습관이면_404() {
        when(habitRepository.findOwned(anyLong(), anyLong())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(USER_ID, 9L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.HABIT_NOT_FOUND);
    }
}
