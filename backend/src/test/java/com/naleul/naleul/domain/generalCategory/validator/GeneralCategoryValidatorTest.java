package com.naleul.naleul.domain.generalCategory.validator;

import com.naleul.naleul.domain.generalCategory.dto.request.GeneralCategoryCreateRequest;
import com.naleul.naleul.domain.generalCategory.dto.request.GeneralCategoryUpdateRequest;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import com.naleul.naleul.global.policy.PlanLimits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GeneralCategoryValidatorTest {

    @Mock
    private GeneralCategoryRepository generalCategoryRepository;

    @InjectMocks
    private GeneralCategoryValidator generalCategoryValidator;

    // 여러 @Nested 그룹에서 공통으로 쓰는 헬퍼라 바깥 클래스에 둠
    private GeneralCategoryCreateRequest someCreateRequest() {
        return GeneralCategoryCreateRequest.builder()
                .generalCategoryName("아침 운동")
                .goalCategoryId(1L)
                .colorId(1L)
                .generalCategoryStartDate(LocalDate.of(2026, 1, 1))
                .generalCategoryEndDate(LocalDate.of(2026, 12, 31))
                .build();
    }

    @Nested
    @DisplayName("validateUpdate()")
    class ValidateUpdate {

        @Test
        void 수정된_시작날짜만_있으면_기존_종료날짜와_비교해서_검증한다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", mock(User.class), mock(GoalCategory.class),
                    mock(UserColor.class), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));

            GeneralCategoryUpdateRequest request = GeneralCategoryUpdateRequest.builder()
                    .generalCategoryStartDate(LocalDate.of(2026, 12, 1))   // 기존 종료일(6/30)보다 늦음
                    .build();

            assertThatThrownBy(() -> generalCategoryValidator.validateUpdate(generalCategory, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_CATEGORY_INVALID_DATE);
        }

        @Test
        void 수정된_종료날짜만_있으면_기존_시작날짜와_비교해서_검증한다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", mock(User.class), mock(GoalCategory.class),
                    mock(UserColor.class), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31));

            GeneralCategoryUpdateRequest request = GeneralCategoryUpdateRequest.builder()
                    .generalCategoryEndDate(LocalDate.of(2026, 1, 1))   // 기존 시작일(6/1)보다 이름
                    .build();

            assertThatThrownBy(() -> generalCategoryValidator.validateUpdate(generalCategory, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_CATEGORY_INVALID_DATE);
        }

        @Test
        void 수정된_시작날짜와_종료날짜가_둘다_있으면_그값끼리_비교해서_검증한다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", mock(User.class), mock(GoalCategory.class),
                    mock(UserColor.class), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

            GeneralCategoryUpdateRequest request = GeneralCategoryUpdateRequest.builder()
                    .generalCategoryStartDate(LocalDate.of(2026, 12, 1))
                    .generalCategoryEndDate(LocalDate.of(2026, 1, 1))
                    .build();

            assertThatThrownBy(() -> generalCategoryValidator.validateUpdate(generalCategory, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_CATEGORY_INVALID_DATE);
        }

        @Test
        void 둘다_없으면_기존_날짜_기준으로_검증을_통과한다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", mock(User.class), mock(GoalCategory.class),
                    mock(UserColor.class), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

            GeneralCategoryUpdateRequest request = GeneralCategoryUpdateRequest.builder()
                    .generalCategoryName("헬스")
                    .build();

            assertThatCode(() -> generalCategoryValidator.validateUpdate(generalCategory, request))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("validateGeneralCategoryLimit()  (validateCreate 경유)")
    class ValidateGeneralCategoryLimit {

        @Test
        void FREE_플랜에서_제한개수에_도달하면_예외가_발생한다() {
            User user = mock(User.class);
            GoalCategory goalCategory = mock(GoalCategory.class);
            given(user.isCurrentlyPremium()).willReturn(false);
            given(goalCategory.getGoalCategoryId()).willReturn(1L);
            given(generalCategoryRepository.countActiveByGoalCategoryId(1L))
                    .willReturn((long) PlanLimits.GENERAL_CATEGORY_FREE_LIMIT);

            assertThatThrownBy(() -> generalCategoryValidator.validateCreate(user, goalCategory, someCreateRequest()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GENERAL_CATEGORY_LIMIT_EXCEEDED);
        }

        @Test
        void PRO_플랜은_개수제한_없이_생성할_수_있다() {
            User user = mock(User.class);
            GoalCategory goalCategory = mock(GoalCategory.class);
            given(user.isCurrentlyPremium()).willReturn(true);

            assertThatCode(() -> generalCategoryValidator.validateCreate(user, goalCategory, someCreateRequest()))
                    .doesNotThrowAnyException();
            verify(generalCategoryRepository, never()).countActiveByGoalCategoryId(any());
        }
    }

    @Nested
    @DisplayName("validateDateRange() (validateCreate 경유)")
    class ValidateDateRange {

        @Test
        void 시작날짜가_종료날짜보다_늦으면_예외가_발생한다() {
            User user = mock(User.class);
            GoalCategory goalCategory = mock(GoalCategory.class);
            given(user.isCurrentlyPremium()).willReturn(true); // 개수 제한 통과시켜서 날짜 검증까지 도달하게 함

            GeneralCategoryCreateRequest request = GeneralCategoryCreateRequest.builder()
                    .generalCategoryName("운동")
                    .goalCategoryId(1L)
                    .colorId(1L)
                    .generalCategoryStartDate(LocalDate.of(2026, 12, 31))
                    .generalCategoryEndDate(LocalDate.of(2026, 1, 1))
                    .build();

            assertThatThrownBy(() -> generalCategoryValidator.validateCreate(user, goalCategory, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_CATEGORY_INVALID_DATE);
        }
    }
}