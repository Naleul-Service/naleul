package com.naleul.naleul.domain.goalCategory.service;

import com.naleul.naleul.domain.category.service.CategoryFinder;
import com.naleul.naleul.domain.goalCategory.deleter.GoalCategoryCascadeDeleter;
import com.naleul.naleul.domain.goalCategory.dto.request.GoalCategoryAchievementRequest;
import com.naleul.naleul.domain.goalCategory.dto.request.GoalCategoryCompleteRequest;
import com.naleul.naleul.domain.goalCategory.dto.request.GoalCategoryCreateRequest;
import com.naleul.naleul.domain.goalCategory.dto.request.GoalCategoryUpdateRequest;
import com.naleul.naleul.domain.goalCategory.dto.response.GoalCategoryResponse;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.event.GoalCategoryCompletedEvent;
import com.naleul.naleul.domain.goalCategory.factory.GoalCategoryFactory;
import com.naleul.naleul.domain.goalCategory.reader.GoalCategoryReader;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.goalCategory.validator.GoalCategoryValidator;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import com.naleul.naleul.domain.userColor.service.UserColorFinder;
import com.naleul.naleul.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * GoalCategoryService 단위 테스트.
 * GoalCategoryReaderTest와 스타일을 맞춰서 BDDMockito(given/willThrow/willDoNothing/then().should())를 쓰고,
 * @DisplayName 대신 메서드 이름 자체를 한글 서술형으로 씁니다.
 * 다만 서비스 메서드가 5개(create/complete/fillAchievement/update/delete)로 많아서,
 * @Nested로 메서드별 그룹을 나눠 트리 구조로 보이게 했습니다.
 *
 * ⚠️ 참고
 *  - GoalCategoryCreateRequest / GoalCategoryCompleteRequest / GoalCategoryUpdateRequest / GoalCategoryResponse 는
 *    @Builder(+@AllArgsConstructor)가 있어서 실제 객체를 만들어 사용합니다.
 *  - GoalCategoryAchievementRequest 는 아직 @NoArgsConstructor + @Getter만 있고 빌더/전체생성자가
 *    없어서 (Jackson이 리플렉션으로 값을 채워주는 구조), 테스트에서 값을 직접 넣을 방법이 없습니다.
 *    이것만 예외적으로 mock()으로 대체해 getter를 스텁했습니다.
 *    → 여기도 @Builder + @AllArgsConstructor를 추가하면 mock() 없이 테스트할 수 있습니다.
 */
@ExtendWith(MockitoExtension.class)
class GoalCategoryServiceTest {

    @Mock private GoalCategoryRepository goalCategoryRepository;
    @Mock private GoalCategoryValidator goalCategoryValidator;
    @Mock private UserFinder userFinder;
    @Mock private UserColorFinder userColorFinder;
    @Mock private CategoryFinder categoryFinder;
    @Mock private GoalCategoryReader goalCategoryReader;
    @Mock private GoalCategoryFactory goalCategoryFactory;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private GoalCategoryCascadeDeleter goalCategoryCascadeDeleter;

    @InjectMocks
    private GoalCategoryService goalCategoryService;

    private static final Long USER_ID = 1L;
    private static final Long GOAL_CATEGORY_ID = 10L;
    private static final Long COLOR_ID = 5L;

    private User user;
    private UserColor userColor;

    @BeforeEach
    void setUp() {
        // User.builder()는 public이라 그대로 사용 가능
        user = User.builder().userId(USER_ID).build();
        // UserColor.builder()도 public (access 지정 없음) 이라 그대로 사용 가능
        userColor = UserColor.builder()
                .userColorId(COLOR_ID)
                .user(user)
                .colorCode("#FFFFFF")
                .isDefault(false)
                .build();
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        void 정상_요청이면_카테고리를_생성하고_저장한다() {
            GoalCategoryCreateRequest request = GoalCategoryCreateRequest.builder()
                    .colorId(COLOR_ID)
                    .goalCategoryName("다이어트")
                    .goalCategoryStartDate(LocalDate.now())
                    .goalCategoryEndDate(LocalDate.now().plusMonths(1))
                    .motive("건강")
                    .build();

            // GoalCategory의 빌더는 private이라 직접 못 만들고, 공개된 정적 팩토리 create()로 대체 생성
            GoalCategory savedGoalCategory = GoalCategory.create(
                    user, userColor, "다이어트", LocalDate.now(), LocalDate.now().plusMonths(1), "건강"
            );
            GoalCategoryResponse expectedResponse = GoalCategoryResponse.builder()
                    .goalCategoryId(savedGoalCategory.getGoalCategoryId())
                    .goalCategoryName("다이어트")
                    .build();

            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(userColorFinder.findUserColor(COLOR_ID, user)).willReturn(userColor);
            given(goalCategoryFactory.create(user, userColor, request)).willReturn(savedGoalCategory);
            given(goalCategoryReader.getResponse(savedGoalCategory.getGoalCategoryId())).willReturn(expectedResponse);

            GoalCategoryResponse result = goalCategoryService.create(USER_ID, request);

            assertThat(result.getGoalCategoryName()).isEqualTo("다이어트");
            then(goalCategoryValidator).should().validateCreate(user, request);
            then(goalCategoryRepository).should().save(savedGoalCategory);
        }

        @Test
        void 검증에_실패하면_생성되지_않는다() {
            GoalCategoryCreateRequest request = GoalCategoryCreateRequest.builder()
                    .colorId(COLOR_ID)
                    .goalCategoryName("다이어트")
                    .goalCategoryStartDate(LocalDate.now())
                    .goalCategoryEndDate(LocalDate.now().plusMonths(1))
                    .build();

            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(userColorFinder.findUserColor(COLOR_ID, user)).willReturn(userColor);
            willThrow(new IllegalArgumentException("유효하지 않은 목표"))
                    .given(goalCategoryValidator).validateCreate(user, request);

            assertThatThrownBy(() -> goalCategoryService.create(USER_ID, request))
                    .isInstanceOf(IllegalArgumentException.class);

            then(goalCategoryFactory).should(never()).create(any(), any(), any());
            then(goalCategoryRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("complete()")
    class Complete {

        @Test
        void 목표를_완료처리하면_이벤트가_발행된다() {
            GoalCategory goalCategory = GoalCategory.create(
                    user, userColor, "다이어트", LocalDate.now().minusDays(10), null, "건강"
            ); // IN_PROGRESS 상태로 생성됨

            GoalCategoryCompleteRequest request = GoalCategoryCompleteRequest.builder()
                    .goalCategoryEndDate(LocalDate.now())
                    .achievement("5kg 감량 성공")
                    .achievementImageUrl("https://image.url/1.png")
                    .build();

            GoalCategoryResponse expectedResponse = GoalCategoryResponse.builder()
                    .goalCategoryId(GOAL_CATEGORY_ID)
                    .achievement("5kg 감량 성공")
                    .build();

            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(categoryFinder.findGoalCategory(GOAL_CATEGORY_ID, user)).willReturn(goalCategory);
            given(goalCategoryReader.getResponse(goalCategory.getGoalCategoryId())).willReturn(expectedResponse);

            GoalCategoryResponse result = goalCategoryService.complete(GOAL_CATEGORY_ID, USER_ID, request);

            assertThat(result.getAchievement()).isEqualTo("5kg 감량 성공");
            assertThat(goalCategory.getGoalCategoryStatus().name()).isEqualTo("COMPLETED");
            // GoalCategoryCompletedEvent는 record(goalCategoryId)라 접근자가 getGoalCategoryId()가 아니라
            // goalCategoryId()입니다. getter 호출 대신 record의 자동 생성 equals()로 비교합니다.
            then(eventPublisher).should().publishEvent(new GoalCategoryCompletedEvent(GOAL_CATEGORY_ID));
        }

        @Test
        void 이미_삭제된_목표를_완료처리하면_예외가_발생하고_이벤트도_발행되지_않는다() {
            GoalCategory goalCategory = GoalCategory.create(user, userColor, "다이어트", LocalDate.now(), null, "건강");
            goalCategory.delete(); // DELETED 상태로 전환 (entity 내부 규칙)

            GoalCategoryCompleteRequest request = GoalCategoryCompleteRequest.builder()
                    .goalCategoryEndDate(LocalDate.now())
                    .build();

            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(categoryFinder.findGoalCategory(GOAL_CATEGORY_ID, user)).willReturn(goalCategory);

            assertThatThrownBy(() -> goalCategoryService.complete(GOAL_CATEGORY_ID, USER_ID, request))
                    .isInstanceOf(CustomException.class);

            then(eventPublisher).should(never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("fillAchievement()")
    class FillAchievement {

        @Test
        void COMPLETED_상태에서_달성기록을_채우면_이벤트가_발행된다() {
            GoalCategory goalCategory = GoalCategory.create(
                    user, userColor, "다이어트", LocalDate.now().minusDays(30), null, "건강"
            );
            // 자동 만료 등으로 이미 COMPLETED 상태가 된 상황을 가정
            goalCategory.complete(LocalDate.now().minusDays(1), null, null);

            GoalCategoryAchievementRequest request = mock(GoalCategoryAchievementRequest.class);
            given(request.getAchievement()).willReturn("뒤늦게 작성한 후기");
            given(request.getAchievementImageUrl()).willReturn("https://image.url/2.png");

            GoalCategoryResponse expectedResponse = GoalCategoryResponse.builder()
                    .goalCategoryId(GOAL_CATEGORY_ID)
                    .achievement("뒤늦게 작성한 후기")
                    .build();

            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(categoryFinder.findGoalCategory(GOAL_CATEGORY_ID, user)).willReturn(goalCategory);
            given(goalCategoryReader.getResponse(GOAL_CATEGORY_ID)).willReturn(expectedResponse);

            GoalCategoryResponse result = goalCategoryService.fillAchievement(GOAL_CATEGORY_ID, USER_ID, request);

            assertThat(result.getAchievement()).isEqualTo("뒤늦게 작성한 후기");
            assertThat(goalCategory.getAchievement()).isEqualTo("뒤늦게 작성한 후기");
            then(eventPublisher).should().publishEvent(any(GoalCategoryCompletedEvent.class));
        }

        @Test
        void COMPLETED가_아니면_예외가_발생한다() {
            // IN_PROGRESS 상태로 생성 (시작일이 오늘 이전/당일이므로 calculateStatusByStartDate에서 IN_PROGRESS)
            GoalCategory goalCategory = GoalCategory.create(user, userColor, "다이어트", LocalDate.now(), null, "건강");
            GoalCategoryAchievementRequest request = mock(GoalCategoryAchievementRequest.class);

            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(categoryFinder.findGoalCategory(GOAL_CATEGORY_ID, user)).willReturn(goalCategory);

            assertThatThrownBy(() -> goalCategoryService.fillAchievement(GOAL_CATEGORY_ID, USER_ID, request))
                    .isInstanceOf(CustomException.class);

            then(eventPublisher).should(never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        void colorId가_있으면_색상도_함께_변경한다() {
            GoalCategory goalCategory = GoalCategory.create(user, userColor, "기존 이름", LocalDate.now(), null, "기존 동기");
            GoalCategoryUpdateRequest request = GoalCategoryUpdateRequest.builder()
                    .colorId(99L)
                    .goalCategoryName("새 이름")
                    .goalCategoryStartDate(LocalDate.now())
                    .goalCategoryEndDate(LocalDate.now().plusDays(10))
                    .motive("새 동기")
                    .build();
            UserColor newColor = UserColor.builder()
                    .userColorId(99L).user(user).colorCode("#000000").isDefault(false).build();

            GoalCategoryResponse expectedResponse = GoalCategoryResponse.builder()
                    .goalCategoryId(GOAL_CATEGORY_ID)
                    .goalCategoryName("새 이름")
                    .build();

            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(categoryFinder.findGoalCategory(GOAL_CATEGORY_ID, user)).willReturn(goalCategory);
            given(userColorFinder.findUserColor(99L, user)).willReturn(newColor);
            given(goalCategoryReader.getResponse(GOAL_CATEGORY_ID)).willReturn(expectedResponse);

            GoalCategoryResponse result = goalCategoryService.update(GOAL_CATEGORY_ID, USER_ID, request);

            assertThat(result.getGoalCategoryName()).isEqualTo("새 이름");
            assertThat(goalCategory.getMotive()).isEqualTo("새 동기");
            assertThat(goalCategory.getUserColor()).isEqualTo(newColor);
            then(goalCategoryValidator).should().validateUpdate(goalCategory, request);
        }

        @Test
        void colorId가_없으면_색상_조회를_건너뛴다() {
            GoalCategory goalCategory = GoalCategory.create(user, userColor, "기존 이름", LocalDate.now(), null, "기존 동기");
            // colorId를 지정하지 않으면 빌더 기본값(null)이 그대로 들어감
            GoalCategoryUpdateRequest request = GoalCategoryUpdateRequest.builder()
                    .goalCategoryName("새 이름")
                    .build();

            GoalCategoryResponse expectedResponse = GoalCategoryResponse.builder()
                    .goalCategoryId(GOAL_CATEGORY_ID)
                    .goalCategoryName("새 이름")
                    .build();

            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(categoryFinder.findGoalCategory(GOAL_CATEGORY_ID, user)).willReturn(goalCategory);
            given(goalCategoryReader.getResponse(GOAL_CATEGORY_ID)).willReturn(expectedResponse);

            goalCategoryService.update(GOAL_CATEGORY_ID, USER_ID, request);

            then(userColorFinder).should(never()).findUserColor(any());
            assertThat(goalCategory.getUserColor()).isEqualTo(userColor); // 색상은 그대로
        }

        @Test
        void 이름만_요청에_있으면_동기와_날짜는_기존값을_유지한다() {
            LocalDate originalStartDate = LocalDate.of(2026, 1, 1);
            LocalDate originalEndDate = LocalDate.of(2026, 12, 31);
            GoalCategory goalCategory = GoalCategory.create(user, userColor, "기존 이름",
                    originalStartDate, originalEndDate, "기존 동기");
            // name만 채우고 나머지는 비워둠 → GoalCategory.update()의 null 체크 분기를 검증
            GoalCategoryUpdateRequest request = GoalCategoryUpdateRequest.builder()
                    .goalCategoryName("새 이름")
                    .build();

            GoalCategoryResponse expectedResponse = GoalCategoryResponse.builder()
                    .goalCategoryId(GOAL_CATEGORY_ID)
                    .goalCategoryName("새 이름")
                    .build();

            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(categoryFinder.findGoalCategory(GOAL_CATEGORY_ID, user)).willReturn(goalCategory);
            given(goalCategoryReader.getResponse(GOAL_CATEGORY_ID)).willReturn(expectedResponse);

            goalCategoryService.update(GOAL_CATEGORY_ID, USER_ID, request);

            assertThat(goalCategory.getGoalCategoryName()).isEqualTo("새 이름");
            assertThat(goalCategory.getMotive()).isEqualTo("기존 동기");
            assertThat(goalCategory.getGoalCategoryStartDate()).isEqualTo(originalStartDate);
            assertThat(goalCategory.getGoalCategoryEndDate()).isEqualTo(originalEndDate);
        }

        @Test
        void 동기만_요청에_있으면_이름과_날짜는_기존값을_유지한다() {
            LocalDate originalStartDate = LocalDate.of(2026, 1, 1);
            LocalDate originalEndDate = LocalDate.of(2026, 12, 31);
            GoalCategory goalCategory = GoalCategory.create(user, userColor, "기존 이름",
                    originalStartDate, originalEndDate, "기존 동기");
            GoalCategoryUpdateRequest request = GoalCategoryUpdateRequest.builder()
                    .motive("새 동기")
                    .build();

            GoalCategoryResponse expectedResponse = GoalCategoryResponse.builder()
                    .goalCategoryId(GOAL_CATEGORY_ID)
                    .build();

            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(categoryFinder.findGoalCategory(GOAL_CATEGORY_ID, user)).willReturn(goalCategory);
            given(goalCategoryReader.getResponse(GOAL_CATEGORY_ID)).willReturn(expectedResponse);

            goalCategoryService.update(GOAL_CATEGORY_ID, USER_ID, request);

            assertThat(goalCategory.getGoalCategoryName()).isEqualTo("기존 이름");
            assertThat(goalCategory.getMotive()).isEqualTo("새 동기");
            assertThat(goalCategory.getGoalCategoryStartDate()).isEqualTo(originalStartDate);
            assertThat(goalCategory.getGoalCategoryEndDate()).isEqualTo(originalEndDate);
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        void 삭제하면_cascade삭제_후_소프트삭제_처리된다() {
            GoalCategory goalCategory = GoalCategory.create(user, userColor, "다이어트", LocalDate.now(), null, "건강");

            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(categoryFinder.findGoalCategory(GOAL_CATEGORY_ID, user)).willReturn(goalCategory);

            goalCategoryService.delete(GOAL_CATEGORY_ID, USER_ID);

            then(goalCategoryCascadeDeleter).should().deleteCascade(GOAL_CATEGORY_ID);
            assertThat(goalCategory.getGoalCategoryStatus().name()).isEqualTo("DELETED");
            assertThat(goalCategory.getUserColor()).isNull();
        }
    }
}