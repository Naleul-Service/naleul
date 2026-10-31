package com.naleul.naleul.domain.generalCategory.service;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.generalCategory.deleter.GeneralCategoryCascadeDeleter;
import com.naleul.naleul.domain.generalCategory.dto.request.GeneralCategoryCompleteRequest;
import com.naleul.naleul.domain.generalCategory.dto.request.GeneralCategoryCreateRequest;
import com.naleul.naleul.domain.generalCategory.dto.request.GeneralCategoryUpdateRequest;
import com.naleul.naleul.domain.generalCategory.dto.response.GeneralCategoryResponse;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.factory.GeneralCategoryFactory;
import com.naleul.naleul.domain.generalCategory.reader.GeneralCategoryReader;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.generalCategory.validator.GeneralCategoryValidator;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.routine.service.RoutineService;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import com.naleul.naleul.domain.userColor.service.UserColorFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class GeneralCategoryServiceTest {

    @Mock private GeneralCategoryRepository generalCategoryRepository;
    @Mock private UserFinder userFinder;
    @Mock private UserColorFinder userColorFinder;
    @Mock private CategoryFinder categoryFinder;
    @Mock private GeneralCategoryReader generalCategoryReader;
    @Mock private GeneralCategoryFactory generalCategoryFactory;
    @Mock private GeneralCategoryValidator generalCategoryValidator;
    @Mock private RoutineService routineService;
    @Mock private GeneralCategoryCascadeDeleter generalCategoryCascadeDeleter;

    @InjectMocks
    private GeneralCategoryService generalCategoryService;

    private static final Long USER_ID = 1L;
    private static final Long GENERAL_CATEGORY_ID = 10L;
    private static final Long GOAL_CATEGORY_ID = 100L;
    private static final Long COLOR_ID = 5L;

    private User user;
    private GoalCategory goalCategory;
    private UserColor userColor;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        goalCategory = mock(GoalCategory.class);
        userColor = mock(UserColor.class);
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        void 정상_요청이면_카테고리를_생성하고_저장한다() {
            GeneralCategoryCreateRequest request = GeneralCategoryCreateRequest.builder()
                    .generalCategoryName("아침 운동")
                    .goalCategoryId(GOAL_CATEGORY_ID)
                    .colorId(COLOR_ID)
                    .generalCategoryStartDate(LocalDate.now())
                    .generalCategoryEndDate(LocalDate.now().plusDays(30))
                    .build();

            GeneralCategory savedGeneralCategory = GeneralCategory.create(
                    "아침 운동", user, goalCategory, userColor, LocalDate.now(), LocalDate.now().plusDays(30));
            GeneralCategoryResponse expectedResponse = GeneralCategoryResponse.builder()
                    .generalCategoryId(GENERAL_CATEGORY_ID)
                    .generalCategoryName("아침 운동")
                    .build();

            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(categoryFinder.findGoalCategory(GOAL_CATEGORY_ID, user)).willReturn(goalCategory);
            given(userColorFinder.findUserColor(COLOR_ID, user)).willReturn(userColor);
            given(generalCategoryFactory.create(user, goalCategory, userColor, request)).willReturn(savedGeneralCategory);
            given(generalCategoryReader.getResponse(savedGeneralCategory.getGeneralCategoryId())).willReturn(expectedResponse);

            GeneralCategoryResponse result = generalCategoryService.create(USER_ID, request);

            assertThat(result.getGeneralCategoryName()).isEqualTo("아침 운동");
            then(generalCategoryValidator).should().validateCreate(user, goalCategory, request);
            then(generalCategoryRepository).should().save(savedGeneralCategory);
        }

        @Test
        void 검증에_실패하면_생성되지_않는다() {
            GeneralCategoryCreateRequest request = GeneralCategoryCreateRequest.builder()
                    .generalCategoryName("아침 운동")
                    .goalCategoryId(GOAL_CATEGORY_ID)
                    .colorId(COLOR_ID)
                    .generalCategoryStartDate(LocalDate.now())
                    .generalCategoryEndDate(LocalDate.now().plusDays(30))
                    .build();

            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(categoryFinder.findGoalCategory(GOAL_CATEGORY_ID, user)).willReturn(goalCategory);
            willThrow(new CustomException(ErrorCode.GENERAL_CATEGORY_LIMIT_EXCEEDED))
                    .given(generalCategoryValidator).validateCreate(user, goalCategory, request);

            assertThatThrownBy(() -> generalCategoryService.create(USER_ID, request))
                    .isInstanceOf(CustomException.class);

            then(generalCategoryFactory).should(never()).create(any(), any(), any(), any());
            then(generalCategoryRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("complete()")
    class Complete {

        @Test
        void 목표를_완료처리하면_상태와_달성내용이_반영된다() {
            GeneralCategory generalCategory = GeneralCategory.create(
                    "아침 운동", user, goalCategory, userColor, LocalDate.now().minusDays(10), null);

            GeneralCategoryCompleteRequest request = GeneralCategoryCompleteRequest.builder()
                    .generalCategoryEndDate(LocalDate.now())
                    .achievement("30분 완주")
                    .achievementImageUrl("https://image.url/1.png")
                    .build();

            GeneralCategoryResponse expectedResponse = GeneralCategoryResponse.builder()
                    .generalCategoryId(GENERAL_CATEGORY_ID)
                    .achievement("30분 완주")
                    .build();

            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(categoryFinder.findGeneralCategory(GENERAL_CATEGORY_ID, user)).willReturn(generalCategory);
            given(generalCategoryReader.getResponse(GENERAL_CATEGORY_ID)).willReturn(expectedResponse);

            GeneralCategoryResponse result = generalCategoryService.complete(GENERAL_CATEGORY_ID, USER_ID, request);

            assertThat(result.getAchievement()).isEqualTo("30분 완주");
            assertThat(generalCategory.getAchievement()).isEqualTo("30분 완주");
        }

        @Test
        void 이미_삭제된_목표를_완료처리하려하면_예외가_발생한다() {
            GeneralCategory generalCategory = GeneralCategory.create(
                    "아침 운동", user, goalCategory, userColor, LocalDate.now(), null);
            generalCategory.delete();

            GeneralCategoryCompleteRequest request = GeneralCategoryCompleteRequest.builder()
                    .generalCategoryEndDate(LocalDate.now())
                    .build();

            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(categoryFinder.findGeneralCategory(GENERAL_CATEGORY_ID, user)).willReturn(generalCategory);

            assertThatThrownBy(() -> generalCategoryService.complete(GENERAL_CATEGORY_ID, USER_ID, request))
                    .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        void goalCategoryId가_다르면_조회해서_변경하고_하위_Routine을_동기화한다() {
            given(goalCategory.getGoalCategoryId()).willReturn(GOAL_CATEGORY_ID);
            GeneralCategory generalCategory = GeneralCategory.create(
                    "기존 이름", user, goalCategory, userColor, LocalDate.now(), LocalDate.now().plusDays(30));
            GoalCategory newGoalCategory = mock(GoalCategory.class);

            GeneralCategoryUpdateRequest request = GeneralCategoryUpdateRequest.builder()
                    .goalCategoryId(999L)
                    .generalCategoryName("새 이름")
                    .build();

            GeneralCategoryResponse expectedResponse = GeneralCategoryResponse.builder()
                    .generalCategoryId(GENERAL_CATEGORY_ID)
                    .generalCategoryName("새 이름")
                    .build();

            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(categoryFinder.findGeneralCategory(GENERAL_CATEGORY_ID, user)).willReturn(generalCategory);
            given(categoryFinder.findGoalCategory(999L, user)).willReturn(newGoalCategory);
            given(generalCategoryReader.getResponse(GENERAL_CATEGORY_ID)).willReturn(expectedResponse);

            GeneralCategoryResponse result = generalCategoryService.update(USER_ID, GENERAL_CATEGORY_ID, request);

            assertThat(result.getGeneralCategoryName()).isEqualTo("새 이름");
            assertThat(generalCategory.getGoalCategory()).isEqualTo(newGoalCategory);
            then(routineService).should().syncGoalCategoryForGeneralCategory(GENERAL_CATEGORY_ID, newGoalCategory);
        }

        @Test
        void goalCategoryId가_지금과_같으면_조회와_동기화를_건너뛴다() {
            given(goalCategory.getGoalCategoryId()).willReturn(GOAL_CATEGORY_ID);
            GeneralCategory generalCategory = GeneralCategory.create(
                    "기존 이름", user, goalCategory, userColor, LocalDate.now(), LocalDate.now().plusDays(30));

            GeneralCategoryUpdateRequest request = GeneralCategoryUpdateRequest.builder()
                    .goalCategoryId(GOAL_CATEGORY_ID)   // 지금 소속과 동일한 id
                    .generalCategoryName("새 이름")
                    .build();

            GeneralCategoryResponse expectedResponse = GeneralCategoryResponse.builder()
                    .generalCategoryId(GENERAL_CATEGORY_ID)
                    .generalCategoryName("새 이름")
                    .build();

            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(categoryFinder.findGeneralCategory(GENERAL_CATEGORY_ID, user)).willReturn(generalCategory);
            given(generalCategoryReader.getResponse(GENERAL_CATEGORY_ID)).willReturn(expectedResponse);

            generalCategoryService.update(USER_ID, GENERAL_CATEGORY_ID, request);

            then(categoryFinder).should(never()).findGoalCategory(any(), any());
            then(routineService).should(never()).syncGoalCategoryForGeneralCategory(any(), any());
            assertThat(generalCategory.getGoalCategory()).isEqualTo(goalCategory); // 그대로 유지
        }

        @Test
        void colorId가_없으면_색상_조회를_건너뛴다() {
            given(goalCategory.getGoalCategoryId()).willReturn(GOAL_CATEGORY_ID);
            GeneralCategory generalCategory = GeneralCategory.create(
                    "기존 이름", user, goalCategory, userColor, LocalDate.now(), LocalDate.now().plusDays(30));

            GeneralCategoryUpdateRequest request = GeneralCategoryUpdateRequest.builder()
                    .generalCategoryName("새 이름")
                    .build();

            GeneralCategoryResponse expectedResponse = GeneralCategoryResponse.builder()
                    .generalCategoryId(GENERAL_CATEGORY_ID)
                    .generalCategoryName("새 이름")
                    .build();

            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(categoryFinder.findGeneralCategory(GENERAL_CATEGORY_ID, user)).willReturn(generalCategory);
            given(generalCategoryReader.getResponse(GENERAL_CATEGORY_ID)).willReturn(expectedResponse);

            generalCategoryService.update(USER_ID, GENERAL_CATEGORY_ID, request);

            then(userColorFinder).should(never()).findUserColor(any(), any());
            assertThat(generalCategory.getColor()).isEqualTo(userColor); // 색상은 그대로
        }

        @Test
        void 검증에_실패하면_수정되지_않는다() {
            GeneralCategory generalCategory = GeneralCategory.create(
                    "기존 이름", user, goalCategory, userColor, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));

            GeneralCategoryUpdateRequest request = GeneralCategoryUpdateRequest.builder()
                    .generalCategoryStartDate(LocalDate.of(2026, 12, 1))   // 기존 종료일보다 늦음
                    .build();

            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(categoryFinder.findGeneralCategory(GENERAL_CATEGORY_ID, user)).willReturn(generalCategory);
            willThrow(new CustomException(ErrorCode.GOAL_CATEGORY_INVALID_DATE))
                    .given(generalCategoryValidator).validateUpdate(generalCategory, request);

            assertThatThrownBy(() -> generalCategoryService.update(USER_ID, GENERAL_CATEGORY_ID, request))
                    .isInstanceOf(CustomException.class);

            then(categoryFinder).should(never()).findGoalCategory(any(), any());
            then(generalCategoryReader).should(never()).getResponse(any());
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        void 삭제하면_cascadeDeleter를_호출한다() {
            GeneralCategory generalCategory = GeneralCategory.create(
                    "아침 운동", user, goalCategory, userColor, LocalDate.now(), LocalDate.now().plusDays(30));

            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(categoryFinder.findGeneralCategory(GENERAL_CATEGORY_ID, user)).willReturn(generalCategory);

            generalCategoryService.delete(USER_ID, GENERAL_CATEGORY_ID);

            then(generalCategoryCascadeDeleter).should().deleteCascade(generalCategory);
        }
    }
}