package com.naleul.naleul.domain.routine.validator;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.routine.dto.request.RoutineCreateRequest;
import com.naleul.naleul.domain.routine.dto.request.RoutineUpdateRequest;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import com.naleul.naleul.global.policy.PlanLimits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class RoutineValidatorTest {

    @Mock
    private RoutineRepository routineRepository;

    private RoutineValidator routineValidator;

    @BeforeEach
    void setUp() {
        routineValidator = new RoutineValidator(routineRepository);
    }

    // ── 공통 픽스처 헬퍼 ──────────────────────────────────

    private User freeUser() {
        User user = mock(User.class);
        given(user.isCurrentlyPremium()).willReturn(false);
        return user;
    }

    private User premiumUser() {
        User user = mock(User.class);
        given(user.isCurrentlyPremium()).willReturn(true);
        return user;
    }

    private GeneralCategory generalCategoryWithId(Long id) {
        GeneralCategory generalCategory = mock(GeneralCategory.class);
        given(generalCategory.getGeneralCategoryId()).willReturn(id);
        return generalCategory;
    }

    private GeneralCategory generalCategoryWithoutStub() {
        return mock(GeneralCategory.class);
    }


    private RoutineCreateRequest validCreateRequest() {
        return RoutineCreateRequest.builder()
                .goalCategoryId(1L)
                .generalCategoryId(1L)
                .routineName("아침 운동")
                .repeatStartDate(LocalDate.of(2026, 9, 1))
                .repeatEndDate(LocalDate.of(2026, 9, 30))
                .repeatStartTime(LocalTime.of(7, 0))
                .repeatEndTime(LocalTime.of(8, 0))
                .repeatDays(List.of(DayOfWeek.MONDAY))
                .notificationEnabled(true)
                .build();
    }

    private Routine routineWithSchedule(LocalDate startDate, LocalDate endDate,
                                        LocalTime startTime, LocalTime endTime) {
        return Routine.create(
                mock(User.class), mock(GoalCategory.class), mock(GeneralCategory.class),
                "테스트 루틴",
                startTime, endTime,
                startDate, endDate,
                List.of(DayOfWeek.MONDAY),
                true
        );
    }

    // ── validateCreate() ──────────────────────────────────

    @Nested
    @DisplayName("validateRoutineLimit() (validateCreate 경유)")
    class ValidateRoutineLimit {

        @Test
        @DisplayName("FREE 플랜에서 제한 개수에 도달하면 예외가 발생한다")
        void FREE_플랜에서_제한개수에_도달하면_예외가_발생한다() {
            // given
            User user = freeUser();
            GeneralCategory generalCategory = generalCategoryWithId(1L);
            given(routineRepository.countActiveByGeneralCategoryId(1L))
                    .willReturn((long) PlanLimits.ROUTINE_FREE_LIMIT);

            RoutineCreateRequest request = validCreateRequest();

            // when & then
            assertThatThrownBy(() -> routineValidator.validateCreate(request, user, generalCategory))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROUTINE_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("PRO(Premium) 플랜은 개수 제한 없이 생성할 수 있다")
        void PRO_플랜은_개수제한_없이_생성할_수_있다() {
            // given
            User user = premiumUser();
            GeneralCategory generalCategory = generalCategoryWithoutStub();
            RoutineCreateRequest request = validCreateRequest();

            // when & then
            assertThatCode(() -> routineValidator.validateCreate(request, user, generalCategory))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("validateDateRange() (validateCreate 경유)")
    class ValidateDateRange {

        @Test
        @DisplayName("시작날짜가 종료날짜보다 늦으면 예외가 발생한다")
        void 시작날짜가_종료날짜보다_늦으면_예외가_발생한다() {
            // given: 개수 제한을 우회하려고 premium 사용
            User user = premiumUser();
            GeneralCategory generalCategory = generalCategoryWithoutStub();

            RoutineCreateRequest request = RoutineCreateRequest.builder()
                    .goalCategoryId(1L)
                    .generalCategoryId(1L)
                    .routineName("아침 운동")
                    .repeatStartDate(LocalDate.of(2026, 9, 10))
                    .repeatEndDate(LocalDate.of(2026, 9, 1))
                    .repeatStartTime(LocalTime.of(7, 0))
                    .repeatEndTime(LocalTime.of(8, 0))
                    .repeatDays(List.of(DayOfWeek.MONDAY))
                    .notificationEnabled(true)
                    .build();

            // when & then
            assertThatThrownBy(() -> routineValidator.validateCreate(request, user, generalCategory))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROUTINE_INVALID_DATE);
        }
    }

    @Nested
    @DisplayName("validateTimeRange() (validateCreate 경유)")
    class ValidateTimeRange {

        @Test
        @DisplayName("시작시간이 종료시간보다 늦으면 예외가 발생한다")
        void 시작시간이_종료시간보다_늦으면_예외가_발생한다() {
            // given
            User user = premiumUser();
            GeneralCategory generalCategory = generalCategoryWithoutStub();

            RoutineCreateRequest request = RoutineCreateRequest.builder()
                    .goalCategoryId(1L)
                    .generalCategoryId(1L)
                    .routineName("아침 운동")
                    .repeatStartDate(LocalDate.of(2026, 9, 1))
                    .repeatEndDate(LocalDate.of(2026, 9, 30))
                    .repeatStartTime(LocalTime.of(20, 0))
                    .repeatEndTime(LocalTime.of(9, 0))
                    .repeatDays(List.of(DayOfWeek.MONDAY))
                    .notificationEnabled(true)
                    .build();

            // when & then
            assertThatThrownBy(() -> routineValidator.validateCreate(request, user, generalCategory))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROUTINE_INVALID_TIME);
        }
    }

    // ── validateUpdate() ──────────────────────────────────

    @Nested
    @DisplayName("validateUpdate()")
    class ValidateUpdate {

        @Test
        @DisplayName("수정된 시작날짜만 있으면 기존 종료날짜와 비교해서 검증한다")
        void 수정된_시작날짜만_있으면_기존_종료날짜와_비교해서_검증한다() {
            // given: 기존 종료일(9/10)보다 늦은 시작일(9/15)만 수정 요청
            Routine routine = routineWithSchedule(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10),
                    LocalTime.of(9, 0), LocalTime.of(10, 0)
            );
            RoutineUpdateRequest request = RoutineUpdateRequest.builder()
                    .repeatStartDate(LocalDate.of(2026, 9, 15))
                    .build();

            // when & then
            assertThatThrownBy(() -> routineValidator.validateUpdate(request, routine))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROUTINE_INVALID_DATE);
        }

        @Test
        @DisplayName("수정된 종료날짜만 있으면 기존 시작날짜와 비교해서 검증한다")
        void 수정된_종료날짜만_있으면_기존_시작날짜와_비교해서_검증한다() {
            // given: 기존 시작일(9/10)보다 이른 종료일(9/5)만 수정 요청
            Routine routine = routineWithSchedule(
                    LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 20),
                    LocalTime.of(9, 0), LocalTime.of(10, 0)
            );
            RoutineUpdateRequest request = RoutineUpdateRequest.builder()
                    .repeatEndDate(LocalDate.of(2026, 9, 5))
                    .build();

            // when & then
            assertThatThrownBy(() -> routineValidator.validateUpdate(request, routine))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROUTINE_INVALID_DATE);
        }

        @Test
        @DisplayName("수정된 시작날짜와 종료날짜가 둘 다 있으면 그 값끼리 비교해서 검증한다")
        void 수정된_시작날짜와_종료날짜가_둘다_있으면_그값끼리_비교해서_검증한다() {
            // given: 기존 날짜(9/1~9/30)는 정상 범위지만, 요청에 온 두 날짜끼리 역전됨
            Routine routine = routineWithSchedule(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                    LocalTime.of(9, 0), LocalTime.of(10, 0)
            );
            RoutineUpdateRequest request = RoutineUpdateRequest.builder()
                    .repeatStartDate(LocalDate.of(2026, 9, 20))
                    .repeatEndDate(LocalDate.of(2026, 9, 10))
                    .build();

            // when & then
            assertThatThrownBy(() -> routineValidator.validateUpdate(request, routine))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROUTINE_INVALID_DATE);
        }

        @Test
        @DisplayName("수정된 시작날짜와 종료날짜가 둘 다 없으면 기존 날짜 기준으로 검증을 통과한다")
        void 수정된_시작날짜와_종료날짜가_둘다_없으면_기존_날짜_기준으로_검증을_통과한다() {
            // given: 기존 날짜 자체가 이미 유효한 범위
            Routine routine = routineWithSchedule(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                    LocalTime.of(9, 0), LocalTime.of(10, 0)
            );
            RoutineUpdateRequest request = RoutineUpdateRequest.builder().build();

            // when & then
            assertThatCode(() -> routineValidator.validateUpdate(request, routine))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("수정된 시작시간만 있으면 기존 종료시간과 비교해서 검증한다")
        void 수정된_시작시간만_있으면_기존_종료시간와_비교해서_검증한다() {
            // given: 기존 종료시간(10:00)보다 늦은 시작시간(11:00)만 수정 요청
            Routine routine = routineWithSchedule(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                    LocalTime.of(9, 0), LocalTime.of(10, 0)
            );
            RoutineUpdateRequest request = RoutineUpdateRequest.builder()
                    .repeatStartTime(LocalTime.of(11, 0))
                    .build();

            // when & then
            assertThatThrownBy(() -> routineValidator.validateUpdate(request, routine))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROUTINE_INVALID_TIME);
        }

        @Test
        @DisplayName("수정된 종료시간만 있으면 기존 시작시간과 비교해서 검증한다")
        void 수정된_종료시간만_있으면_기존_시작시간와_비교해서_검증한다() {
            // given: 기존 시작시간(20:00)보다 이른 종료시간(19:00)만 수정 요청
            Routine routine = routineWithSchedule(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                    LocalTime.of(20, 0), LocalTime.of(21, 0)
            );
            RoutineUpdateRequest request = RoutineUpdateRequest.builder()
                    .repeatEndTime(LocalTime.of(19, 0))
                    .build();

            // when & then
            assertThatThrownBy(() -> routineValidator.validateUpdate(request, routine))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROUTINE_INVALID_TIME);
        }

        @Test
        @DisplayName("수정된 시작시간과 종료시간이 둘 다 있으면 그 값끼리 비교해서 검증한다")
        void 수정된_시작시간와_종료시간가_둘다_있으면_그값끼리_비교해서_검증한다() {
            // given: 기존 시간(9:00~10:00)은 정상 범위지만, 요청에 온 두 시간끼리 역전됨
            Routine routine = routineWithSchedule(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                    LocalTime.of(9, 0), LocalTime.of(10, 0)
            );
            RoutineUpdateRequest request = RoutineUpdateRequest.builder()
                    .repeatStartTime(LocalTime.of(15, 0))
                    .repeatEndTime(LocalTime.of(14, 0))
                    .build();

            // when & then
            assertThatThrownBy(() -> routineValidator.validateUpdate(request, routine))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROUTINE_INVALID_TIME);
        }

        @Test
        @DisplayName("수정된 시작시간과 종료시간이 둘 다 없으면 기존 시간 기준으로 검증을 통과한다")
        void 수정된_시작시간와_종료시간가_둘다_없으면_기존_날짜_기준으로_검증을_통과한다() {
            // given: 기존 시간 자체가 이미 유효한 범위
            Routine routine = routineWithSchedule(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                    LocalTime.of(9, 0), LocalTime.of(10, 0)
            );
            RoutineUpdateRequest request = RoutineUpdateRequest.builder().build();

            // when & then
            assertThatCode(() -> routineValidator.validateUpdate(request, routine))
                    .doesNotThrowAnyException();
        }
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("자정을 넘기는 루틴: 23:00 ~ 00:00 · 22:00 ~ 02:00 은 되고, 12시간을 넘으면 안 된다")
    void overnight_range() {
        org.assertj.core.api.Assertions.assertThat(RoutineValidator.isValidTimeRange(LocalTime.of(23, 0), LocalTime.of(0, 0))).isTrue();
        org.assertj.core.api.Assertions.assertThat(RoutineValidator.isValidTimeRange(LocalTime.of(22, 0), LocalTime.of(2, 0))).isTrue();
        org.assertj.core.api.Assertions.assertThat(RoutineValidator.isValidTimeRange(LocalTime.of(20, 0), LocalTime.of(9, 0))).isFalse();
        org.assertj.core.api.Assertions.assertThat(RoutineValidator.isValidTimeRange(LocalTime.of(9, 0), LocalTime.of(9, 0))).isFalse();
    }
}
