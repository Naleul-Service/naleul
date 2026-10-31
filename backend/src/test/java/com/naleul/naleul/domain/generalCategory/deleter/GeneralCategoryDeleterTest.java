package com.naleul.naleul.domain.generalCategory.deleter;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.routine.service.RoutineService;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GeneralCategoryCascadeDeleterTest {

    @Mock
    private RoutineService routineService;

    @InjectMocks
    private GeneralCategoryCascadeDeleter generalCategoryCascadeDeleter;

    @Test
    void 자기자신을_소프트삭제하고_하위_Routine을_모두_삭제한다() {
        // GeneralCategory.builder()가 public이라 테스트에서 id를 직접 지정 가능
        // (실제로는 findGeneralCategory(...)로 조회해온, id가 이미 채워진 엔티티가 들어옴)
        GeneralCategory generalCategory = GeneralCategory.builder()
                .generalCategoryId(10L)
                .generalCategoryStatus(GoalCategoryStatus.IN_PROGRESS)
                .color(mock(UserColor.class))
                .build();

        generalCategoryCascadeDeleter.deleteCascade(generalCategory);

        assertThat(generalCategory.getGeneralCategoryStatus()).isEqualTo(GoalCategoryStatus.DELETED);
        assertThat(generalCategory.getColor()).isNull();
        then(routineService).should().deleteAllByGeneralCategoryId(10L);
    }
}