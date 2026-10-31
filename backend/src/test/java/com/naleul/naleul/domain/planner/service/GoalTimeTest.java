package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.planner.dto.response.GoalTimeResponse;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskPriority;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoalTimeTest {

    private static final LocalDate D = LocalDate.of(2026, 11, 18);
    private static final LocalDateTime FROM = D.atStartOfDay();
    private static final LocalDateTime TO = D.plusDays(1).atStartOfDay();

    private static Task done(LocalDateTime s, LocalDateTime e) {
        Task t = Task.create(null, null, null, "t", TaskPriority.C, s, e);
        ReflectionTestUtils.setField(t, "taskStatus", TaskStatus.COMPLETED);
        return t;
    }

    private static GoalCategory goal(long id, String name) {
        GoalCategory g = GoalCategory.create(null, null, name, D, null, null);
        ReflectionTestUtils.setField(g, "goalCategoryId", id);
        return g;
    }

    @Test
    @DisplayName("실제 시각이 있으면 실제, 없으면 계획 시각으로 센다")
    void actualFirst() {
        Task t = done(D.atTime(9, 0), D.atTime(10, 0));
        assertThat(GoalTimeQueryService.minutesOf(t, null, List.of(), List.of(), FROM, TO, D, D)).isEqualTo(60);
        DateTimeRange actual = new DateTimeRange(D.atTime(9, 0), D.atTime(9, 40));
        assertThat(GoalTimeQueryService.minutesOf(t, actual, List.of(), List.of(), FROM, TO, D, D)).isEqualTo(40);
    }

    @Test
    @DisplayName("나눠서 한 구간은 모두 더하고, 기간 밖은 잘라낸다")
    void segmentsAndClip() {
        Task t = done(D.atTime(9, 0), D.atTime(10, 0));
        List<DateTimeRange> segs = List.of(
                new DateTimeRange(D.atTime(13, 0), D.atTime(14, 0)),
                new DateTimeRange(D.atTime(23, 30), D.plusDays(1).atTime(0, 30)));
        assertThat(GoalTimeQueryService.minutesOf(t, null, segs, List.of(), FROM, TO, D, D)).isEqualTo(90);
    }

    @Test
    @DisplayName("목표별로 합쳐 많이 쓴 순으로, 목표 없는 Task 는 null 한 줄")
    void summarize() {
        GoalCategory a = goal(1L, "운동");
        GoalCategory b = goal(2L, "영어");
        GoalTimeResponse r = GoalTimeQueryService.summarize(D, D, List.of(
                new GoalTimeQueryService.Spent(a, 30),
                new GoalTimeQueryService.Spent(b, 50),
                new GoalTimeQueryService.Spent(a, 40),
                new GoalTimeQueryService.Spent(null, 20)));
        assertThat(r.totalMinutes()).isEqualTo(140);
        assertThat(r.goals()).extracting(GoalTimeResponse.GoalTime::goalCategoryId).containsExactly(1L, 2L, null);
        assertThat(r.goals().get(0).minutes()).isEqualTo(70);
        assertThat(r.goals().get(0).taskCount()).isEqualTo(2);
    }
}
