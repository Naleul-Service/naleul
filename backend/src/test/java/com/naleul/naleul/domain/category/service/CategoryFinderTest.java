package com.naleul.naleul.domain.category.service;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.user.entity.User;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CategoryFinderTest {

    @Mock
    private GoalCategoryRepository goalCategoryRepository;

    @Mock
    private GeneralCategoryRepository generalCategoryRepository;

    @InjectMocks
    private CategoryFinder categoryFinder;

    @Nested
    @DisplayName("findGoalCategory()")
    class FindGoalCategory {

        @Test
        void 본인_소유_목표는_정상_조회된다() {
            User user = mock(User.class);
            GoalCategory goalCategory = GoalCategory.create(user, mock(UserColor.class), "운동",
                    LocalDate.now(), LocalDate.now().plusDays(30), "동기부여");
            given(goalCategoryRepository.findByGoalCategoryIdAndUser(10L, user, GoalCategoryStatus.DELETED))
                    .willReturn(Optional.of(goalCategory));

            GoalCategory result = categoryFinder.findGoalCategory(10L, user);

            assertThat(result.getGoalCategoryName()).isEqualTo("운동");
        }

        @Test
        void 존재하지_않거나_남의_목표면_예외가_발생한다() {
            User user = mock(User.class);
            given(goalCategoryRepository.findByGoalCategoryIdAndUser(anyLong(), any(), any()))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> categoryFinder.findGoalCategory(10L, user))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_CATEGORY_NOT_FOUND);
        }

        @Test
        void goalCategoryId가_null이면_null을_리턴한다() {
            // 선택적 연관관계 호출부(예: 카테고리 미지정을 허용하는 요청)를 위한 케이스.
            // 현재 실사용 호출부(@PathVariable 기반)에서는 이 분기가 실행되지 않음.
            GoalCategory result = categoryFinder.findGoalCategory(null, mock(User.class));

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("findGeneralCategory() (단건)")
    class FindGeneralCategory {

        @Test
        void 본인_소유_일반카테고리는_정상_조회된다() {
            User user = mock(User.class);
            GeneralCategory generalCategory = mock(GeneralCategory.class);
            given(generalCategoryRepository.findByGeneralCategoryIdAndUser(5L, user, GoalCategoryStatus.DELETED))
                    .willReturn(Optional.of(generalCategory));

            GeneralCategory result = categoryFinder.findGeneralCategory(5L, user);

            assertThat(result).isEqualTo(generalCategory);
        }

        @Test
        void 존재하지_않거나_남의_일반카테고리면_예외가_발생한다() {
            User user = mock(User.class);
            given(generalCategoryRepository.findByGeneralCategoryIdAndUser(anyLong(), any(), any()))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> categoryFinder.findGeneralCategory(5L, user))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GENERAL_CATEGORY_NOT_FOUND);
        }

        @Test
        void generalCategoryId가_null이면_null을_리턴한다() {
            GeneralCategory result = categoryFinder.findGeneralCategory(null, mock(User.class));

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("findGeneralCategories() (목록)")
    class FindGeneralCategories {

        @Test
        void 요청한_id_전부가_본인_소유면_정상_조회된다() {
            User user = mock(User.class);
            GeneralCategory gc1 = mock(GeneralCategory.class);
            GeneralCategory gc2 = mock(GeneralCategory.class);
            given(generalCategoryRepository.findAllByGeneralCategoryIdInAndUser(
                    List.of(1L, 2L), user, GoalCategoryStatus.DELETED))
                    .willReturn(List.of(gc1, gc2));

            List<GeneralCategory> result = categoryFinder.findGeneralCategories(List.of(1L, 2L), user);

            assertThat(result).containsExactly(gc1, gc2);
        }

        @Test
        void 요청한_개수와_조회된_개수가_다르면_예외가_발생한다() {
            User user = mock(User.class);
            given(generalCategoryRepository.findAllByGeneralCategoryIdInAndUser(
                    List.of(1L, 2L), user, GoalCategoryStatus.DELETED))
                    .willReturn(List.of(mock(GeneralCategory.class)));   // 2개 요청, 1개만 조회됨 (남의 것이거나 DELETED)

            assertThatThrownBy(() -> categoryFinder.findGeneralCategories(List.of(1L, 2L), user))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GENERAL_CATEGORY_NOT_FOUND);
        }
    }
}