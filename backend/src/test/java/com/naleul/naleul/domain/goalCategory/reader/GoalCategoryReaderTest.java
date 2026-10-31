package com.naleul.naleul.domain.goalCategory.reader;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.goalCategory.dto.response.GoalCategoryResponse;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GoalCategoryReaderTest {

    @Mock
    private GoalCategoryRepository goalCategoryRepository;

    @Mock
    private UserFinder userFinder;

    @Mock
    private CategoryFinder categoryFinder;

    @InjectMocks
    private GoalCategoryReader goalCategoryReader;

    @Nested
    @DisplayName("getResponse()")
    class GetResponse {

        @Test
        void 존재하는_id로_조회하면_응답을_반환한다() {
            GoalCategory goalCategory = GoalCategory.create(mock(User.class), mock(UserColor.class), "운동",
                    LocalDate.now(), LocalDate.now().plusDays(30), "동기부여");
            given(goalCategoryRepository.findByIdWithAll(1L)).willReturn(Optional.of(goalCategory));

            GoalCategoryResponse response = goalCategoryReader.getResponse(1L);

            assertThat(response.getGoalCategoryName()).isEqualTo("운동");
        }

        @Test
        void 존재하지_않는_id면_예외가_발생한다() {
            given(goalCategoryRepository.findByIdWithAll(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> goalCategoryReader.getResponse(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_CATEGORY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getGoalCategory()")
    class GetGoalCategory {

        @Test
        void 본인_소유_목표를_userId로_조회하면_응답을_반환한다() {
            User user = mock(User.class);
            given(userFinder.findUser(1L)).willReturn(user);

            GoalCategory goalCategory = GoalCategory.create(user, mock(UserColor.class), "운동",
                    LocalDate.now(), LocalDate.now().plusDays(30), "동기부여");
            given(categoryFinder.findGoalCategory(10L, user)).willReturn(goalCategory);

            GoalCategoryResponse response = goalCategoryReader.getGoalCategory(10L, 1L);

            assertThat(response.getGoalCategoryName()).isEqualTo("운동");
        }

        @Test
        void 다른_사용자의_목표를_조회하려하면_예외가_발생한다() {
            User user = mock(User.class);
            given(userFinder.findUser(1L)).willReturn(user);
            given(categoryFinder.findGoalCategory(10L, user))
                    .willThrow(new CustomException(ErrorCode.GOAL_CATEGORY_NOT_FOUND));

            assertThatThrownBy(() -> goalCategoryReader.getGoalCategory(10L, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_CATEGORY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getGoalCategories()")
    class GetGoalCategories {

        @Test
        void 존재하는_유저면_목표카테고리_목록을_반환한다() {
            willDoNothing().given(userFinder).validateUserExists(1L);

            GoalCategory goalCategory = GoalCategory.create(mock(User.class), mock(UserColor.class), "운동",
                    LocalDate.now(), LocalDate.now().plusDays(30), "동기부여");
            given(goalCategoryRepository.findAllByUserIdWithAll(1L)).willReturn(List.of(goalCategory));

            List<GoalCategoryResponse> result = goalCategoryReader.getGoalCategories(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getGoalCategoryName()).isEqualTo("운동");
        }

        @Test
        void 존재하지_않는_유저면_예외가_발생한다() {
            willThrow(new CustomException(ErrorCode.USER_NOT_FOUND))
                    .given(userFinder).validateUserExists(999L);

            assertThatThrownBy(() -> goalCategoryReader.getGoalCategories(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
        }

        @Test
        void 목표카테고리가_없으면_빈_리스트를_반환한다() {
            willDoNothing().given(userFinder).validateUserExists(1L);
            given(goalCategoryRepository.findAllByUserIdWithAll(1L)).willReturn(List.of());

            List<GoalCategoryResponse> result = goalCategoryReader.getGoalCategories(1L);

            assertThat(result).isEmpty();
        }
    }
}