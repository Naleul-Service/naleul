package com.naleul.naleul.domain.generalCategory.factory;

import com.naleul.naleul.domain.generalCategory.dto.request.GeneralCategoryCreateRequest;
import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GeneralCategoryFactoryTest {

    private final GeneralCategoryFactory generalCategoryFactory = new GeneralCategoryFactory();

    private final User user = mock(User.class);
    private final GoalCategory goalCategory = mock(GoalCategory.class);
    private final UserColor color = mock(UserColor.class);

    @Test
    void request값으로_GeneralCategory를_생성한다() {
        GeneralCategoryCreateRequest request = GeneralCategoryCreateRequest.builder()
                .generalCategoryName("아침 운동")
                .goalCategoryId(1L)   // Factory는 이 값을 직접 쓰지 않음 (조회는 서비스 책임)
                .colorId(1L)          // 위와 동일한 이유로 Factory 입장에선 무관
                .generalCategoryStartDate(LocalDate.now())
                .generalCategoryEndDate(LocalDate.now().plusDays(30))
                .build();

        GeneralCategory result = generalCategoryFactory.create(user, goalCategory, color, request);

        assertThat(result.getGeneralCategoryName()).isEqualTo("아침 운동");
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getGoalCategory()).isEqualTo(goalCategory);
        assertThat(result.getColor()).isEqualTo(color);
        assertThat(result.getGeneralCategoryStartDate()).isEqualTo(LocalDate.now());
        assertThat(result.getGeneralCategoryEndDate()).isEqualTo(LocalDate.now().plusDays(30));
    }

    @Test
    void 시작일이_미래인_request로_생성하면_NOT_STARTED_상태가_된다() {
        GeneralCategoryCreateRequest request = GeneralCategoryCreateRequest.builder()
                .generalCategoryName("아침 운동")
                .goalCategoryId(1L)
                .colorId(1L)
                .generalCategoryStartDate(LocalDate.now().plusDays(7))
                .generalCategoryEndDate(LocalDate.now().plusDays(37))
                .build();

        GeneralCategory result = generalCategoryFactory.create(user, goalCategory, color, request);

        assertThat(result.getGeneralCategoryStatus()).isEqualTo(GoalCategoryStatus.NOT_STARTED);
    }
}