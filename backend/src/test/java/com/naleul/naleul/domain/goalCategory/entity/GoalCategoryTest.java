package com.naleul.naleul.domain.goalCategory.entity;

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

class GoalCategoryTest {

    private final User user = mock(User.class);
    private final UserColor color = mock(UserColor.class);

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        void 시작일이_미래면_NOT_STARTED_상태로_생성된다() {
            GoalCategory goalCategory = GoalCategory.create(user, color, "운동",
                    LocalDate.now().plusDays(7), LocalDate.now().plusDays(37), "동기부여");

            assertThat(goalCategory.getGoalCategoryStatus()).isEqualTo(GoalCategoryStatus.NOT_STARTED);
        }

        @Test
        void 시작일이_오늘이면_IN_PROGRESS_상태로_생성된다() {
            GoalCategory goalCategory = GoalCategory.create(user, color, "운동",
                    LocalDate.now(), LocalDate.now().plusDays(30), "동기부여");

            assertThat(goalCategory.getGoalCategoryStatus()).isEqualTo(GoalCategoryStatus.IN_PROGRESS);
        }

        @Test
        void 시작일이_과거면_IN_PROGRESS_상태로_생성된다() {
            GoalCategory goalCategory = GoalCategory.create(user, color, "운동",
                    LocalDate.now().minusDays(3), LocalDate.now().plusDays(30), "동기부여");

            assertThat(goalCategory.getGoalCategoryStatus()).isEqualTo(GoalCategoryStatus.IN_PROGRESS);
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        void 이름만_수정하면_다른_필드는_유지된다() {
            GoalCategory goalCategory = GoalCategory.create(user, color, "운동",
                    LocalDate.now(), LocalDate.now().plusDays(30), "동기부여");

            goalCategory.update("헬스", null, null, null);

            assertThat(goalCategory.getGoalCategoryName()).isEqualTo("헬스");
            assertThat(goalCategory.getMotive()).isEqualTo("동기부여");
        }

        @Test
        void 시작일을_미래로_수정하면_NOT_STARTED로_재계산된다() {
            GoalCategory goalCategory = GoalCategory.create(user, color, "운동",
                    LocalDate.now(), LocalDate.now().plusDays(30), "동기부여");   // IN_PROGRESS로 시작

            goalCategory.update(null, LocalDate.now().plusDays(10), null, null);

            assertThat(goalCategory.getGoalCategoryStatus()).isEqualTo(GoalCategoryStatus.NOT_STARTED);
        }

        @Test
        void 날짜를_안바꾸면_상태도_재계산되지_않고_그대로_유지된다() {
            GoalCategory goalCategory = GoalCategory.create(user, color, "운동",
                    LocalDate.now(), LocalDate.now().plusDays(30), "동기부여");   // IN_PROGRESS

            goalCategory.update("헬스", null, null, null);

            assertThat(goalCategory.getGoalCategoryStatus()).isEqualTo(GoalCategoryStatus.IN_PROGRESS);
        }

        @Test
        void COMPLETED_상태에서는_날짜를_수정해도_상태가_재계산되지_않는다() {
            GoalCategory goalCategory = GoalCategory.create(user, color, "운동",
                    LocalDate.now(), LocalDate.now().plusDays(30), "동기부여");
            goalCategory.complete(LocalDate.now(), "달성!", null);

            goalCategory.update(null, LocalDate.now().plusDays(10), null, null);

            assertThat(goalCategory.getGoalCategoryStatus()).isEqualTo(GoalCategoryStatus.COMPLETED);
        }

        @Test
        void DELETED_상태에서는_날짜를_수정해도_상태가_재계산되지_않는다() {
            GoalCategory goalCategory = GoalCategory.create(user, color, "운동",
                    LocalDate.now(), LocalDate.now().plusDays(30), "동기부여");
            goalCategory.delete();

            goalCategory.update(null, LocalDate.now().plusDays(10), null, null);

            assertThat(goalCategory.getGoalCategoryStatus()).isEqualTo(GoalCategoryStatus.DELETED);
        }
    }

    @Nested
    @DisplayName("start()")
    class Start {

        @Test
        void NOT_STARTED_상태에서_start_호출시_IN_PROGRESS로_바뀐다() {
            GoalCategory goalCategory = GoalCategory.create(user, color, "운동",
                    LocalDate.now().plusDays(1), LocalDate.now().plusDays(30), "동기부여");

            goalCategory.start();

            assertThat(goalCategory.getGoalCategoryStatus()).isEqualTo(GoalCategoryStatus.IN_PROGRESS);
        }

        @Test
        void IN_PROGRESS_상태에서_start_호출해도_상태가_바뀌지_않는다() {
            GoalCategory goalCategory = GoalCategory.create(user, color, "운동",
                    LocalDate.now(), LocalDate.now().plusDays(30), "동기부여");

            goalCategory.start();

            assertThat(goalCategory.getGoalCategoryStatus()).isEqualTo(GoalCategoryStatus.IN_PROGRESS);
        }
    }

    @Nested
    @DisplayName("expire()")
    class Expire {

        @Test
        void IN_PROGRESS_목표는_expire_호출시_COMPLETED로_바뀐다() {
            GoalCategory goalCategory = GoalCategory.create(user, color, "운동",
                    LocalDate.now(), LocalDate.now().plusDays(30), "동기부여");

            goalCategory.expire();

            assertThat(goalCategory.getGoalCategoryStatus()).isEqualTo(GoalCategoryStatus.COMPLETED);
        }

        @Test
        void NOT_STARTED_목표는_expire_호출해도_상태가_바뀌지_않는다() {
            GoalCategory goalCategory = GoalCategory.create(user, color, "운동",
                    LocalDate.now().plusDays(1), LocalDate.now().plusDays(30), "동기부여");

            goalCategory.expire();

            assertThat(goalCategory.getGoalCategoryStatus()).isEqualTo(GoalCategoryStatus.NOT_STARTED);
        }

        @Test
        void DELETED_목표는_expire_호출해도_상태가_바뀌지_않는다() {
            GoalCategory goalCategory = GoalCategory.create(user, color, "운동",
                    LocalDate.now(), LocalDate.now().plusDays(30), "동기부여");
            goalCategory.delete();

            goalCategory.expire();

            assertThat(goalCategory.getGoalCategoryStatus()).isEqualTo(GoalCategoryStatus.DELETED);
        }

        @Test
        void COMPLETED_목표는_expire_호출해도_상태가_바뀌지_않는다() {
            GoalCategory goalCategory = GoalCategory.create(user, color, "운동",
                    LocalDate.now(), LocalDate.now().plusDays(30), "동기부여");
            goalCategory.complete(LocalDate.now(), "달성!", null);

            goalCategory.expire();

            assertThat(goalCategory.getGoalCategoryStatus()).isEqualTo(GoalCategoryStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("complete()")
    class Complete {

        @Test
        void complete_호출시_상태와_달성내용이_설정된다() {
            GoalCategory goalCategory = GoalCategory.create(user, color, "운동",
                    LocalDate.now(), LocalDate.now().plusDays(30), "동기부여");

            goalCategory.complete(LocalDate.now(), "3kg 감량 성공", "http://image.url");

            assertThat(goalCategory.getGoalCategoryStatus()).isEqualTo(GoalCategoryStatus.COMPLETED);
            assertThat(goalCategory.getAchievement()).isEqualTo("3kg 감량 성공");
            assertThat(goalCategory.getAchievementImageUrl()).isEqualTo("http://image.url");
        }

        @Test
        void NOT_STARTED_목표도_complete_호출하면_완료처리된다() {
            GoalCategory goalCategory = GoalCategory.create(user, color, "운동",
                    LocalDate.now().plusDays(7), LocalDate.now().plusDays(30), "동기부여");

            goalCategory.complete(LocalDate.now(), "포기함", null);

            assertThat(goalCategory.getGoalCategoryStatus()).isEqualTo(GoalCategoryStatus.COMPLETED);
        }

        @Test
        void 삭제된_목표를_완료처리하려하면_예외가_발생한다() {
            GoalCategory goalCategory = GoalCategory.create(user, color, "운동",
                    LocalDate.now(), LocalDate.now().plusDays(30), "동기부여");
            goalCategory.delete();

            assertThatThrownBy(() -> goalCategory.complete(LocalDate.now(), "달성!", null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_CATEGORY_ALREADY_DELETED);
        }
    }

    @Nested
    @DisplayName("fillAchievement()")
    class FillAchievement {

        @Test
        void 완료된_목표에_achievement를_입력하면_정상_반영된다() {
            GoalCategory goalCategory = GoalCategory.create(user, color, "운동",
                    LocalDate.now().minusDays(30), LocalDate.now().minusDays(1), "동기부여");
            goalCategory.expire();   // achievement 없이 자동 완료된 상태

            goalCategory.fillAchievement("3kg 감량 성공", "http://image.url");

            assertThat(goalCategory.getAchievement()).isEqualTo("3kg 감량 성공");
            assertThat(goalCategory.getAchievementImageUrl()).isEqualTo("http://image.url");
        }

        @Test
        void 완료되지_않은_목표에_achievement를_입력하려하면_예외가_발생한다() {
            GoalCategory goalCategory = GoalCategory.create(user, color, "운동",
                    LocalDate.now(), LocalDate.now().plusDays(30), "동기부여");   // IN_PROGRESS

            assertThatThrownBy(() -> goalCategory.fillAchievement("달성!", null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_CATEGORY_NOT_COMPLETED);
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        void 삭제하면_상태가_DELETED가_되고_색상참조가_해제된다() {
            GoalCategory goalCategory = GoalCategory.create(user, color, "운동",
                    LocalDate.now(), LocalDate.now().plusDays(30), "동기부여");

            goalCategory.delete();

            assertThat(goalCategory.getGoalCategoryStatus()).isEqualTo(GoalCategoryStatus.DELETED);
            assertThat(goalCategory.getUserColor()).isNull();
        }
    }
}