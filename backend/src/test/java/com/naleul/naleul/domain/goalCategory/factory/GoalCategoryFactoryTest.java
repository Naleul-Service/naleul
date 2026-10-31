package com.naleul.naleul.domain.goalCategory.factory;

import com.naleul.naleul.domain.goalCategory.dto.request.GoalCategoryCreateRequest;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GoalCategoryFactoryTest {

    private final GoalCategoryFactory goalCategoryFactory = new GoalCategoryFactory();

    @Test
    void request값으로_GoalCategory를_생성한다() {
        User user = mock(User.class);
        UserColor color = mock(UserColor.class);
        GoalCategoryCreateRequest request = GoalCategoryCreateRequest.builder()
                .goalCategoryName("운동")
                .goalCategoryStartDate(LocalDate.of(2026, 1, 1))
                .goalCategoryEndDate(LocalDate.of(2026, 12, 31))
                .motive("건강해지고 싶어서")
                .build();

        GoalCategory result = goalCategoryFactory.create(user, color, request);

        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getUserColor()).isEqualTo(color);
        assertThat(result.getGoalCategoryName()).isEqualTo("운동");
        assertThat(result.getGoalCategoryStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(result.getGoalCategoryEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(result.getMotive()).isEqualTo("건강해지고 싶어서");
        assertThat(result.getGoalCategoryStatus()).isEqualTo(GoalCategoryStatus.IN_PROGRESS);
    }

    @Test
    void 시작일이_미래인_request로_생성하면_NOT_STARTED_상태가_된다() {
        GoalCategoryCreateRequest request = GoalCategoryCreateRequest.builder()
                .goalCategoryName("운동")
                .goalCategoryStartDate(LocalDate.now().plusDays(7))
                .goalCategoryEndDate(LocalDate.now().plusDays(37))
                .motive("동기부여")
                .build();

        GoalCategory result = goalCategoryFactory.create(mock(User.class), mock(UserColor.class), request);

        assertThat(result.getGoalCategoryStatus()).isEqualTo(GoalCategoryStatus.NOT_STARTED);
    }
}