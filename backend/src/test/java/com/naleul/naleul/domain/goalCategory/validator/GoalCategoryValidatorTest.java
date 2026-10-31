package com.naleul.naleul.domain.goalCategory.validator;

import com.naleul.naleul.domain.goalCategory.dto.request.GoalCategoryCreateRequest;
import com.naleul.naleul.domain.goalCategory.dto.request.GoalCategoryUpdateRequest;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
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
class GoalCategoryValidatorTest {

    @Mock
    private GoalCategoryRepository goalCategoryRepository;

    @InjectMocks
    private GoalCategoryValidator goalCategoryValidator;

    // 여러 @Nested 그룹에서 공통으로 쓰는 헬퍼라 바깥 클래스에 둠
    // (비-static 이너 클래스라 @Nested 안에서도 그대로 호출 가능)
    private GoalCategoryCreateRequest someCreateRequest() {
        return GoalCategoryCreateRequest.builder()
                .goalCategoryName("운동")
                .colorId(1L)
                .goalCategoryStartDate(LocalDate.of(2026, 1, 1))
                .goalCategoryEndDate(LocalDate.of(2026, 12, 31))
                .build();
    }

    @Nested
    @DisplayName("validateUpdate()")
    class ValidateUpdate {

        @Test
        void 수정된_시작날짜만_있으면_기존_종료날짜와_비교해서_검증한다() {
            GoalCategory goalCategory = GoalCategory.create(mock(User.class), mock(UserColor.class), "운동",
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), "동기부여");

            GoalCategoryUpdateRequest request = GoalCategoryUpdateRequest.builder()
                    .goalCategoryStartDate(LocalDate.of(2026, 12, 1))   // 기존 종료일(6/30)보다 늦음
                    .build();

            assertThatThrownBy(() -> goalCategoryValidator.validateUpdate(goalCategory, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_CATEGORY_INVALID_DATE);
        }

        @Test
        void 수정된_종료날짜만_있으면_기존_시작날짜와_비교해서_검증한다() {
            GoalCategory goalCategory = GoalCategory.create(mock(User.class), mock(UserColor.class), "운동",
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31), "동기부여");

            GoalCategoryUpdateRequest request = GoalCategoryUpdateRequest.builder()
                    .goalCategoryEndDate(LocalDate.of(2026, 1, 1))   // 기존 시작일(6/1)보다 이름
                    .build();

            assertThatThrownBy(() -> goalCategoryValidator.validateUpdate(goalCategory, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_CATEGORY_INVALID_DATE);
        }

        @Test
        void 수정된_시작날짜와_종료날짜가_둘다_있으면_그값끼리_비교해서_검증한다() {
            GoalCategory goalCategory = GoalCategory.create(mock(User.class), mock(UserColor.class), "운동",
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "동기부여");

            GoalCategoryUpdateRequest request = GoalCategoryUpdateRequest.builder()
                    .goalCategoryStartDate(LocalDate.of(2026, 12, 1))
                    .goalCategoryEndDate(LocalDate.of(2026, 1, 1))
                    .build();

            assertThatThrownBy(() -> goalCategoryValidator.validateUpdate(goalCategory, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_CATEGORY_INVALID_DATE);
        }

        @Test
        void 둘다_없으면_기존_날짜_기준으로_검증을_통과한다() {
            GoalCategory goalCategory = GoalCategory.create(mock(User.class), mock(UserColor.class), "운동",
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "동기부여");

            GoalCategoryUpdateRequest request = GoalCategoryUpdateRequest.builder()
                    .goalCategoryName("헬스")
                    .build();

            assertThatCode(() -> goalCategoryValidator.validateUpdate(goalCategory, request))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("validateGoalCategoryLimit() (validateCreate 경유)")
    class ValidateGoalCategoryLimit {

        @Test
        void FREE_플랜에서_제한개수에_도달하면_예외가_발생한다() {
            User user = mock(User.class);
            given(user.isCurrentlyPremium()).willReturn(false);
            given(user.getUserId()).willReturn(1L);
            given(goalCategoryRepository.countActiveByUserId(1L))
                    .willReturn((long) PlanLimits.GOAL_CATEGORY_FREE_LIMIT);

            assertThatThrownBy(() -> goalCategoryValidator.validateCreate(user, someCreateRequest()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_CATEGORY_LIMIT_EXCEEDED);
        }

        @Test
        void FREE_플랜에서_제한개수보다_1개_적으면_생성이_허용된다() {
            User user = mock(User.class);
            given(user.isCurrentlyPremium()).willReturn(false);
            given(user.getUserId()).willReturn(1L);
            given(goalCategoryRepository.countActiveByUserId(1L))
                    .willReturn((long) PlanLimits.GOAL_CATEGORY_FREE_LIMIT - 1);

            assertThatCode(() -> goalCategoryValidator.validateCreate(user, someCreateRequest()))
                    .doesNotThrowAnyException();
        }

        @Test
        void PRO_플랜은_개수제한_없이_생성할_수_있다() {
            User user = mock(User.class);
            given(user.isCurrentlyPremium()).willReturn(true);

            assertThatCode(() -> goalCategoryValidator.validateCreate(user, someCreateRequest()))
                    .doesNotThrowAnyException();
            verify(goalCategoryRepository, never()).countActiveByUserId(any());
        }
    }

    @Nested
    @DisplayName("validateDateRange() (validateCreate 경유)")
    class ValidateDateRange {

        @Test
        void 시작날짜가_종료날짜보다_늦으면_예외가_발생한다() {
            User user = mock(User.class);
            given(user.isCurrentlyPremium()).willReturn(true);

            GoalCategoryCreateRequest request = GoalCategoryCreateRequest.builder()
                    .goalCategoryStartDate(LocalDate.of(2026, 12, 31))
                    .goalCategoryEndDate(LocalDate.of(2026, 1, 1))
                    .build();

            assertThatThrownBy(() -> goalCategoryValidator.validateCreate(user, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_CATEGORY_INVALID_DATE);
        }
    }
}