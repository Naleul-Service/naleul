package com.naleul.naleul.domain.goalProgress.service;

import com.naleul.naleul.domain.goalProgress.dto.response.GoalProgressResponse.*;
import com.naleul.naleul.domain.goalProgress.service.GoalProgressCalculator.MetricInput;
import com.naleul.naleul.domain.goalProgress.service.GoalProgressCalculator.MilestoneInput;
import com.naleul.naleul.domain.goalProgress.service.GoalProgressCalculator.TaskInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 예시 목표: 2주 동안 72kg → 69kg (10/1 ~ 10/15), 오늘 10/8 (딱 절반 지점 → 계획상 70.5kg)
 */
class GoalProgressCalculatorTest {

    static final LocalDate START = LocalDate.of(2026, 10, 1);
    static final LocalDate END = LocalDate.of(2026, 10, 15);
    static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    static MetricInput weight(String current) {
        return new MetricInput("체중", "kg", bd("72"), current == null ? null : bd(current), bd("69"));
    }

    static Log log(int day, String value) {
        return new Log(LocalDate.of(2026, 10, day), bd(value), null);
    }

    @Nested
    @DisplayName("수치 목표")
    class Metric {

        @Test
        void 계획선은_시작에서_종료일_목표값까지_곧게_이어지고_오늘_기대값은_절반지점() {
            MetricProgress p = GoalProgressCalculator.metric(TODAY, START, END, weight("70.5"), List.of(), List.of());

            assertThat(p.planLine()).extracting(Point::date).containsExactly(START, END);
            assertThat(p.expectedValue()).isEqualByComparingTo("70.5");
            assertThat(p.status()).isEqualTo(Status.ON_TRACK);
            assertThat(p.progressPercent()).isEqualByComparingTo("50.0");
        }

        @Test
        void 수치가_있는_마일스톤을_꺾은점으로_쓴다() {
            List<MilestoneInput> ms = List.of(
                    new MilestoneInput("1주 차", LocalDate.of(2026, 10, 8), bd("71.5")),
                    new MilestoneInput("수치 없음", LocalDate.of(2026, 10, 10), null),
                    new MilestoneInput("2주 차", END, bd("69")));

            MetricProgress p = GoalProgressCalculator.metric(TODAY, START, END, weight("71.5"), ms, List.of());

            assertThat(p.planLine()).extracting(Point::date)
                    .containsExactly(START, LocalDate.of(2026, 10, 8), END);
            assertThat(p.expectedValue()).isEqualByComparingTo("71.5");
            assertThat(p.status()).isEqualTo(Status.ON_TRACK);
        }

        @Test
        void 감량_목표에서_기대값보다_가벼우면_AHEAD_무거우면_BEHIND() {
            assertThat(GoalProgressCalculator.metric(TODAY, START, END, weight("70.0"), List.of(), List.of()).status())
                    .isEqualTo(Status.AHEAD);
            assertThat(GoalProgressCalculator.metric(TODAY, START, END, weight("71.5"), List.of(), List.of()).status())
                    .isEqualTo(Status.BEHIND);
        }

        @Test
        void 늘리기_목표는_방향이_반대() {
            MetricInput books = new MetricInput("읽은 책", "권", bd("0"), bd("6"), bd("10"));   // 절반 지점 기대 5권

            assertThat(GoalProgressCalculator.metric(TODAY, START, END, books, List.of(), List.of()).status())
                    .isEqualTo(Status.AHEAD);
        }

        @Test
        void 목표값을_넘으면_ACHIEVED() {
            assertThat(GoalProgressCalculator.metric(TODAY, START, END, weight("68.8"), List.of(), List.of()).status())
                    .isEqualTo(Status.ACHIEVED);
        }

        @Test
        void 종료일이_없으면_기대값_없이_NO_PLAN() {
            MetricProgress p = GoalProgressCalculator.metric(TODAY, START, null, weight("71"), List.of(), List.of());

            assertThat(p.expectedValue()).isNull();
            assertThat(p.status()).isEqualTo(Status.NO_PLAN);
        }

        @Test
        void 시작값이나_목표값이_없으면_수치_목표가_아니다() {
            assertThat(GoalProgressCalculator.metric(TODAY, START, END,
                    new MetricInput("체중", "kg", null, null, bd("69")), List.of(), List.of())).isNull();
        }

        @Test
        void 오늘_기록이_있으면_loggedToday() {
            MetricProgress p = GoalProgressCalculator.metric(TODAY, START, END, weight("71"),
                    List.of(), List.of(log(8, "71"), log(2, "71.8")));

            assertThat(p.loggedToday()).isTrue();
            assertThat(p.lastLoggedDate()).isEqualTo(TODAY);
            assertThat(p.logs()).extracting(Log::date).isSorted();
        }

        @Test
        void 최근_추세대로면_목표_도달_예상일을_준다() {
            // 하루 0.2kg씩 감소 → 10/8 70.6kg, 69kg까지 8일 더 → 10/16
            List<Log> logs = List.of(log(2, "71.8"), log(4, "71.4"), log(6, "71.0"), log(8, "70.6"));

            MetricProgress p = GoalProgressCalculator.metric(TODAY, START, END, weight("70.6"), List.of(), logs);

            assertThat(p.projectedDate()).isEqualTo(LocalDate.of(2026, 10, 16));
        }

        @Test
        void 추세가_반대거나_기록이_부족하면_예상일_없음() {
            List<Log> gaining = List.of(log(2, "71.0"), log(5, "71.5"), log(8, "72.0"));
            assertThat(GoalProgressCalculator.metric(TODAY, START, END, weight("72"), List.of(), gaining).projectedDate())
                    .isNull();

            List<Log> few = List.of(log(7, "71.0"), log(8, "70.8"));
            assertThat(GoalProgressCalculator.metric(TODAY, START, END, weight("70.8"), List.of(), few).projectedDate())
                    .isNull();
        }
    }

    @Nested
    @DisplayName("Task 진행")
    class Tasks {

        static TaskInput task(int plannedDay, Integer doneDay) {
            return new TaskInput(LocalDate.of(2026, 10, plannedDay), doneDay == null ? null : LocalDate.of(2026, 10, doneDay));
        }

        @Test
        void 오늘까지_할_Task_대비_완료율과_날짜별_누적() {
            List<TaskInput> tasks = List.of(
                    task(2, 2), task(4, 5), task(6, null),   // 오늘까지 3개 중 2개 완료
                    task(10, 7),                             // 미리 끝낸 Task 도 "할 일"로 센다
                    task(12, null));

            TaskProgress p = GoalProgressCalculator.tasks(TODAY, START, END, tasks);

            assertThat(p.total()).isEqualTo(5);
            assertThat(p.completed()).isEqualTo(3);
            assertThat(p.dueByToday()).isEqualTo(4);
            assertThat(p.completionPercent()).isEqualByComparingTo("75.0");

            assertThat(p.series()).hasSize(15);
            TaskPoint today = p.series().get(7);
            assertThat(today.date()).isEqualTo(TODAY);
            assertThat(today.planned()).isEqualTo(3);
            assertThat(today.completed()).isEqualTo(3);
            TaskPoint last = p.series().get(14);
            assertThat(last.planned()).isEqualTo(5);
            assertThat(last.completed()).isNull();       // 미래는 아직 모름
        }

        @Test
        void 기간이_길면_주_단위로_줄이되_오늘과_종료일은_꼭_넣는다() {
            LocalDate start = LocalDate.of(2026, 1, 1);
            LocalDate end = LocalDate.of(2026, 12, 31);

            List<TaskPoint> series = GoalProgressCalculator.tasks(TODAY, start, end, List.of()).series();

            assertThat(series.size()).isLessThan(60);
            assertThat(series).extracting(TaskPoint::date).contains(start, TODAY, end).isSorted();
        }
    }
}
