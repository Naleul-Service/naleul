package com.naleul.naleul.domain.goalCategory.support;

import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** 진행 중인 목표의 "멈춤" 판정 */
class GoalPauseCalculatorTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 9);
    static final LocalDate START = LocalDate.of(2026, 8, 1);

    private static Integer life(LocalDate last) {
        return GoalPauseCalculator.pausedDays(GoalCategoryStatus.IN_PROGRESS, false, false, START, last, TODAY);
    }

    @Test
    void 생활형은_14일_활동이_없으면_멈춤() {
        assertThat(life(TODAY.minusDays(13))).isNull();
        assertThat(life(TODAY.minusDays(14))).isEqualTo(14);
    }

    @Test
    void 활동이_한_번도_없으면_시작일부터_센다() {
        assertThat(life(null)).isEqualTo(69);   // 8/1 → 10/9
        assertThat(GoalPauseCalculator.pausedDays(GoalCategoryStatus.IN_PROGRESS, false, false,
                TODAY.minusDays(5), null, TODAY)).isNull();   // 시작한 지 5일
    }

    @Test
    void 업무형은_28일() {
        assertThat(GoalPauseCalculator.pausedDays(GoalCategoryStatus.IN_PROGRESS, false, true,
                START, TODAY.minusDays(20), TODAY)).isNull();
        assertThat(GoalPauseCalculator.pausedDays(GoalCategoryStatus.IN_PROGRESS, false, true,
                null, TODAY.minusDays(30), TODAY)).isEqualTo(30);
    }

    @Test
    void 진행_중이_아니거나_임시_목표면_멈춤이_아니다() {
        assertThat(GoalPauseCalculator.pausedDays(GoalCategoryStatus.COMPLETED, false, false,
                START, null, TODAY)).isNull();
        assertThat(GoalPauseCalculator.pausedDays(GoalCategoryStatus.NOT_STARTED, false, false,
                START, null, TODAY)).isNull();
        assertThat(GoalPauseCalculator.pausedDays(GoalCategoryStatus.IN_PROGRESS, true, false,
                START, null, TODAY)).isNull();
    }
}
