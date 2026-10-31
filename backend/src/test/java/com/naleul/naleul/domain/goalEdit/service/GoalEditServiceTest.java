package com.naleul.naleul.domain.goalEdit.service;

import com.naleul.naleul.domain.generalCategory.dto.response.GeneralCategoryResponse.RoutineSummary;
import com.naleul.naleul.domain.goalCategory.dto.response.GoalCategoryResponse;
import com.naleul.naleul.domain.goalCategory.dto.response.GoalCategoryResponse.GeneralCategoryInfo;
import com.naleul.naleul.domain.goalCategory.dto.response.GoalCategoryResponse.MilestoneInfo;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.goalEdit.dto.GoalEditOp;
import com.naleul.naleul.domain.goalEdit.dto.GoalEditOpType;
import com.naleul.naleul.domain.goalEdit.dto.GoalEditPreviewResponse.Change;
import com.naleul.naleul.domain.milestone.enums.MilestoneStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 말로 고치기 — AI 변경안 검사 · 전/후 문장 (DB·AI 없이) */
class GoalEditServiceTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    private static GoalEditService.Snapshot snapshot() {
        RoutineSummary run = RoutineSummary.builder()
                .routineId(11L).routineName("유산소 30분")
                .repeatDays(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
                .repeatStartTime(LocalTime.of(19, 30)).durationMinutes(30).build();
        GeneralCategoryInfo area = GeneralCategoryInfo.builder()
                .generalCategoryId(5L).generalCategoryName("운동").generalCategoryStatus(GoalCategoryStatus.IN_PROGRESS)
                .routines(List.of(run)).build();
        MilestoneInfo ms = MilestoneInfo.builder()
                .milestoneId(21L).title("1개월 차").dueDate(LocalDate.of(2026, 11, 7)).status(MilestoneStatus.PENDING).build();
        GoalCategoryResponse goal = GoalCategoryResponse.builder()
                .goalCategoryId(1L).goalCategoryName("10kg 감량").goalCategoryStatus(GoalCategoryStatus.IN_PROGRESS)
                .goalCategoryStartDate(LocalDate.of(2026, 10, 1)).goalCategoryEndDate(LocalDate.of(2027, 1, 1))
                .metricUnit("kg").startValue(new BigDecimal("80")).targetValue(new BigDecimal("72"))
                .generalCategories(List.of(area)).milestones(List.of(ms)).build();
        return new GoalEditService.Snapshot(goal, Map.of(11L, run), Map.of(11L, 5L), Map.of(21L, ms), Map.of(5L, area));
    }

    private static GoalEditOp op(GoalEditOpType type, Long targetId, String title, List<Weekday> days, LocalDate date) {
        return new GoalEditOp(type, targetId, title, null, days, null, null, date, null, null, null);
    }

    @Test
    void 이_목표에_없는_id는_버린다() {
        assertThat(GoalEditService.sanitize(op(GoalEditOpType.DELETE_ROUTINE, 999L, null, null, null), snapshot(), TODAY)).isNull();
        assertThat(GoalEditService.sanitize(op(GoalEditOpType.UPDATE_MILESTONE, 999L, "x", null, null), snapshot(), TODAY)).isNull();
    }

    @Test
    void 루틴_빈도_변경은_전후를_사람이_읽게() {
        GoalEditOp clean = GoalEditService.sanitize(
                op(GoalEditOpType.UPDATE_ROUTINE, 11L, null, List.of(Weekday.MON, Weekday.THU), null), snapshot(), TODAY);

        Change change = GoalEditService.describe(clean, snapshot());

        assertThat(change.action()).isEqualTo("변경");
        assertThat(change.before()).contains("월·수·금");
        assertThat(change.after()).contains("월·목").contains("19:30");
    }

    @Test
    void 지난_날짜나_목표_기간_밖은_쓰지_않는다() {
        assertThat(GoalEditService.sanitize(op(GoalEditOpType.ADD_TASK, null, "모의고사", null, TODAY.minusDays(1)), snapshot(), TODAY)).isNull();
        assertThat(GoalEditService.sanitize(op(GoalEditOpType.ADD_MILESTONE, null, "점검", null, LocalDate.of(2027, 2, 1)), snapshot(), TODAY)).isNull();
        // 종료일이 이상하면 그 필드만 빠지고, 남는 게 없으면 변경 자체가 없음
        assertThat(GoalEditService.sanitize(op(GoalEditOpType.UPDATE_GOAL, null, null, null, TODAY.minusDays(3)), snapshot(), TODAY)).isNull();
    }

    @Test
    void 종료일을_늦추면_점검_시점이_자동으로_맞춰진다고_알려준다() {
        GoalEditOp clean = GoalEditService.sanitize(
                op(GoalEditOpType.UPDATE_GOAL, null, null, null, LocalDate.of(2027, 1, 15)), snapshot(), TODAY);

        Change change = GoalEditService.describe(clean, snapshot());

        assertThat(change.before()).isEqualTo("종료 1월 1일");
        assertThat(change.after()).startsWith("종료 1월 15일").contains("자동");
    }

    @Test
    void 루틴_추가는_요일이_있어야_하고_길이는_범위로_자른다() {
        assertThat(GoalEditService.sanitize(op(GoalEditOpType.ADD_ROUTINE, null, "스트레칭", null, null), snapshot(), TODAY)).isNull();

        GoalEditOp add = new GoalEditOp(GoalEditOpType.ADD_ROUTINE, null, "스트레칭", 2000, List.of(Weekday.TUE),
                null, null, null, null, null, 777L);
        GoalEditOp clean = GoalEditService.sanitize(add, snapshot(), TODAY);

        assertThat(clean.durationMinutes()).isEqualTo(24 * 60);
        assertThat(clean.areaId()).isNull();   // 없는 영역이면 "기타 할 일"로
    }
}
