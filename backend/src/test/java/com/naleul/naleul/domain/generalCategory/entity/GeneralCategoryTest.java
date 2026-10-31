package com.naleul.naleul.domain.generalCategory.entity;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GeneralCategoryTest {

    private final User user = mock(User.class);
    private final GoalCategory goalCategory = mock(GoalCategory.class);
    private final UserColor color = mock(UserColor.class);

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        void 시작일이_미래면_NOT_STARTED_상태로_생성된다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", user, goalCategory, color,
                    LocalDate.now().plusDays(7), LocalDate.now().plusDays(37));

            assertThat(generalCategory.getGeneralCategoryStatus()).isEqualTo(GoalCategoryStatus.NOT_STARTED);
        }

        @Test
        void 시작일이_오늘이면_IN_PROGRESS_상태로_생성된다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", user, goalCategory, color,
                    LocalDate.now(), LocalDate.now().plusDays(30));

            assertThat(generalCategory.getGeneralCategoryStatus()).isEqualTo(GoalCategoryStatus.IN_PROGRESS);
        }

        @Test
        void 시작일이_과거면_IN_PROGRESS_상태로_생성된다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", user, goalCategory, color,
                    LocalDate.now().minusDays(3), LocalDate.now().plusDays(30));

            assertThat(generalCategory.getGeneralCategoryStatus()).isEqualTo(GoalCategoryStatus.IN_PROGRESS);
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        void 이름만_수정하면_다른_필드는_유지된다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", user, goalCategory, color,
                    LocalDate.now(), LocalDate.now().plusDays(30));

            generalCategory.update("헬스", null, null, null, null);

            assertThat(generalCategory.getGeneralCategoryName()).isEqualTo("헬스");
            assertThat(generalCategory.getGoalCategory()).isEqualTo(goalCategory);
            assertThat(generalCategory.getColor()).isEqualTo(color);
        }

        @Test
        void 시작일을_미래로_수정하면_NOT_STARTED로_재계산된다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", user, goalCategory, color,
                    LocalDate.now(), LocalDate.now().plusDays(30));   // IN_PROGRESS로 시작

            generalCategory.update(null, null, null, LocalDate.now().plusDays(10), null);

            assertThat(generalCategory.getGeneralCategoryStatus()).isEqualTo(GoalCategoryStatus.NOT_STARTED);
        }

        @Test
        void 날짜를_안바꾸면_상태도_재계산되지_않고_그대로_유지된다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", user, goalCategory, color,
                    LocalDate.now(), LocalDate.now().plusDays(30));   // IN_PROGRESS

            generalCategory.update("헬스", null, null, null, null);

            assertThat(generalCategory.getGeneralCategoryStatus()).isEqualTo(GoalCategoryStatus.IN_PROGRESS);
        }

        @Test
        void COMPLETED_상태에서는_날짜를_수정해도_상태가_재계산되지_않는다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", user, goalCategory, color,
                    LocalDate.now(), LocalDate.now().plusDays(30));
            generalCategory.complete(LocalDate.now(), "달성!", null);

            generalCategory.update(null, null, null, LocalDate.now().plusDays(10), null);

            assertThat(generalCategory.getGeneralCategoryStatus()).isEqualTo(GoalCategoryStatus.COMPLETED);
        }

        @Test
        void DELETED_상태에서는_날짜를_수정해도_상태가_재계산되지_않는다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", user, goalCategory, color,
                    LocalDate.now(), LocalDate.now().plusDays(30));
            generalCategory.delete();

            generalCategory.update(null, null, null, LocalDate.now().plusDays(10), null);

            assertThat(generalCategory.getGeneralCategoryStatus()).isEqualTo(GoalCategoryStatus.DELETED);
        }
    }

    @Nested
    @DisplayName("start()")
    class Start {

        @Test
        void NOT_STARTED_상태에서_start_호출시_IN_PROGRESS로_바뀐다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", user, goalCategory, color,
                    LocalDate.now().plusDays(1), LocalDate.now().plusDays(30));

            generalCategory.start();

            assertThat(generalCategory.getGeneralCategoryStatus()).isEqualTo(GoalCategoryStatus.IN_PROGRESS);
        }

        @Test
        void COMPLETED_상태에서_start_호출해도_상태가_바뀌지_않는다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", user, goalCategory, color,
                    LocalDate.now(), LocalDate.now().plusDays(30));
            generalCategory.complete(LocalDate.now(), "달성!", null);

            generalCategory.start();

            assertThat(generalCategory.getGeneralCategoryStatus()).isEqualTo(GoalCategoryStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("complete()")
    class Complete {

        @Test
        void complete_호출시_상태와_달성내용이_설정된다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", user, goalCategory, color,
                    LocalDate.now(), LocalDate.now().plusDays(30));

            generalCategory.complete(LocalDate.now(), "3kg 감량 성공", "http://image.url");

            assertThat(generalCategory.getGeneralCategoryStatus()).isEqualTo(GoalCategoryStatus.COMPLETED);
            assertThat(generalCategory.getActualEndDate()).isEqualTo(LocalDate.now());
            assertThat(generalCategory.getAchievement()).isEqualTo("3kg 감량 성공");
            assertThat(generalCategory.getAchievementImageUrl()).isEqualTo("http://image.url");
        }

        @Test
        void NOT_STARTED_목표도_complete_호출하면_완료처리된다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", user, goalCategory, color,
                    LocalDate.now().plusDays(7), LocalDate.now().plusDays(30));

            generalCategory.complete(LocalDate.now(), "포기함", null);

            assertThat(generalCategory.getGeneralCategoryStatus()).isEqualTo(GoalCategoryStatus.COMPLETED);
        }

        @Test
        void DELETED_상태에서는_완료처리시_에러를_발생한다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", user, goalCategory, color,
                    LocalDate.now(), LocalDate.now().plusDays(30));
            generalCategory.delete();

            assertThatThrownBy(() -> generalCategory.complete(LocalDate.now(), "달성!", null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_CATEGORY_ALREADY_DELETED);
        }
    }

    @Nested
    @DisplayName("expire()")
    class Expire {

        @Test
        void IN_PROGRESS_목표는_expire_호출시_COMPLETED로_바뀐다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", user, goalCategory, color,
                    LocalDate.now(), LocalDate.now().plusDays(30));

            generalCategory.expire();

            assertThat(generalCategory.getGeneralCategoryStatus()).isEqualTo(GoalCategoryStatus.COMPLETED);
        }

        @Test
        void NOT_STARTED_목표는_expire_호출해도_상태가_바뀌지_않는다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", user, goalCategory, color,
                    LocalDate.now().plusDays(1), LocalDate.now().plusDays(30));

            generalCategory.expire();

            assertThat(generalCategory.getGeneralCategoryStatus()).isEqualTo(GoalCategoryStatus.NOT_STARTED);
        }

        @Test
        void DELETED_목표는_expire_호출해도_상태가_바뀌지_않는다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", user, goalCategory, color,
                    LocalDate.now(), LocalDate.now().plusDays(30));
            generalCategory.delete();

            generalCategory.expire();

            assertThat(generalCategory.getGeneralCategoryStatus()).isEqualTo(GoalCategoryStatus.DELETED);
        }

        @Test
        void COMPLETED_목표는_expire_호출해도_상태가_바뀌지_않는다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", user, goalCategory, color,
                    LocalDate.now(), LocalDate.now().plusDays(30));
            generalCategory.complete(LocalDate.now(), "달성!", null);

            generalCategory.expire();

            assertThat(generalCategory.getGeneralCategoryStatus()).isEqualTo(GoalCategoryStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        void 삭제하면_상태가_DELETED가_되고_색상참조가_해제된다() {
            GeneralCategory generalCategory = GeneralCategory.create("운동", user, goalCategory, color,
                    LocalDate.now(), LocalDate.now().plusDays(30));

            generalCategory.delete();

            assertThat(generalCategory.getGeneralCategoryStatus()).isEqualTo(GoalCategoryStatus.DELETED);
            assertThat(generalCategory.getColor()).isNull();
        }
    }
}