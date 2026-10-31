package com.naleul.naleul.domain.generalCategory.reader;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.generalCategory.dto.response.GeneralCategoryResponse;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
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
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GeneralCategoryReaderTest {

    @Mock
    private GeneralCategoryRepository generalCategoryRepository;

    @Mock
    private UserFinder userFinder;

    @Mock
    private CategoryFinder categoryFinder;

    @InjectMocks
    private GeneralCategoryReader generalCategoryReader;

    private GeneralCategory sampleGeneralCategory(User user) {
        GoalCategory goalCategory = mock(GoalCategory.class);
        given(goalCategory.getGoalCategoryId()).willReturn(1L);
        given(goalCategory.getGoalCategoryName()).willReturn("건강");

        return GeneralCategory.create("운동", user, goalCategory, mock(UserColor.class),
                LocalDate.now(), LocalDate.now().plusDays(30));
    }

    @Nested
    @DisplayName("getResponse()")
    class GetResponse {

        @Test
        void 존재하는_ID이면_응답을_반환한다() {
            GeneralCategory generalCategory = sampleGeneralCategory(mock(User.class));
            given(generalCategoryRepository.findByIdWithAll(1L)).willReturn(Optional.of(generalCategory));

            GeneralCategoryResponse response = generalCategoryReader.getResponse(1L);

            assertThat(response.getGeneralCategoryName()).isEqualTo("운동");
        }

        @Test
        void 존재하지_않는_ID이면_예외가_발생한다() {
            given(generalCategoryRepository.findByIdWithAll(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> generalCategoryReader.getResponse(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GENERAL_CATEGORY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getGeneralCategory()")
    class GetGeneralCategory {

        @Test
        void 본인_소유_목표를_userId로_조회하면_응답을_반환한다() {
            User user = mock(User.class);
            given(userFinder.findUser(1L)).willReturn(user);

            GeneralCategory generalCategory = sampleGeneralCategory(user);
            given(categoryFinder.findGeneralCategory(10L, user)).willReturn(generalCategory);

            GeneralCategoryResponse response = generalCategoryReader.getGeneralCategory(1L, 10L);

            assertThat(response.getGeneralCategoryName()).isEqualTo("운동");
        }

        @Test
        void 다른_사용자의_목표를_조회하려하면_예외가_발생한다() {
            User user = mock(User.class);
            given(userFinder.findUser(1L)).willReturn(user);
            given(categoryFinder.findGeneralCategory(10L, user))
                    .willThrow(new CustomException(ErrorCode.GENERAL_CATEGORY_NOT_FOUND));

            assertThatThrownBy(() -> generalCategoryReader.getGeneralCategory(1L, 10L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GENERAL_CATEGORY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getGeneralCategories()")
    class GetGeneralCategories {

        @Test
        void 존재하는_유저면_목표카테고리_목록을_반환한다() {
            User user = mock(User.class);
            given(userFinder.findUser(1L)).willReturn(user);

            GeneralCategory generalCategory = sampleGeneralCategory(user);
            given(generalCategoryRepository.findAllByUser(user)).willReturn(List.of(generalCategory));

            List<GeneralCategoryResponse> result = generalCategoryReader.getGeneralCategories(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getGeneralCategoryName()).isEqualTo("운동");
        }

        @Test
        void 존재하지_않는_유저면_예외가_발생한다() {
            given(userFinder.findUser(999L)).willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

            assertThatThrownBy(() -> generalCategoryReader.getGeneralCategories(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
        }

        @Test
        void 목표카테고리가_없으면_빈_리스트를_반환한다() {
            User user = mock(User.class);
            given(userFinder.findUser(1L)).willReturn(user);
            given(generalCategoryRepository.findAllByUser(user)).willReturn(List.of());

            List<GeneralCategoryResponse> result = generalCategoryReader.getGeneralCategories(1L);

            assertThat(result).isEmpty();
        }
    }
}