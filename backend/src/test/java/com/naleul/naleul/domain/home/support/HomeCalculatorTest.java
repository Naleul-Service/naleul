package com.naleul.naleul.domain.home.support;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalProgress.enums.ReflectionMood;
import com.naleul.naleul.domain.home.dto.response.HomeTodayResponse;
import com.naleul.naleul.domain.home.dto.response.HomeTodayResponse.Deadline;
import com.naleul.naleul.domain.home.dto.response.HomeTodayResponse.DeadlineType;
import com.naleul.naleul.domain.home.dto.response.HomeTodayResponse.GoalToday;
import com.naleul.naleul.domain.home.support.HomeCalculator.TopicDeadline;
import com.naleul.naleul.domain.retrospective.entity.Retrospective;
import com.naleul.naleul.domain.retrospective.enums.ReviewType;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.work.dto.response.WorkBoardResponse;
import com.naleul.naleul.domain.work.dto.response.WorkBoardResponse.TopicStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HomeCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);
    private long seq = 100;
    private GoalCategory work;
    private GoalCategory sqld;
    private GoalCategory done;

    @BeforeEach
    void setUp() {
        work = GoalCategory.createRecord(null, null, "나를 앱 v2", "💼", null, TODAY.minusDays(10));
        sqld = GoalCategory.create(null, null, "SQLD 합격", TODAY.minusDays(14), TODAY.plusDays(20), null);
        done = GoalCategory.create(null, null, "끝난 목표", TODAY.minusDays(60), TODAY.plusDays(5), null);
        ReflectionTestUtils.setField(work, "goalCategoryId", 1L);
        ReflectionTestUtils.setField(sqld, "goalCategoryId", 2L);
        ReflectionTestUtils.setField(done, "goalCategoryId", 3L);
        ReflectionTestUtils.setField(work, "goalCategoryStatus", GoalCategoryStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(sqld, "goalCategoryStatus", GoalCategoryStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(done, "goalCategoryStatus", GoalCategoryStatus.COMPLETED);
    }

    private Task task(String name, GoalCategory g, LocalDate day, LocalDate due) {
        Task t = Task.builder().taskName(name).taskStatus(TaskStatus.TODO).goalCategory(g)
                .routineDate(day).dueDate(due).build();
        ReflectionTestUtils.setField(t, "taskId", seq++);
        return t;
    }

    private Task at(Task t, LocalDate day, int hour) {
        ReflectionTestUtils.setField(t, "plannedStartAt", day.atTime(hour, 0));
        return t;
    }

    private Task completedOn(Task t, LocalDate day) {
        ReflectionTestUtils.setField(t, "taskStatus", TaskStatus.COMPLETED);
        ReflectionTestUtils.setField(t, "completedAt", day.atTime(18, 0));
        return t;
    }

    private static WorkBoardResponse.Topic topic(long id, String name, LocalDate due, TopicStatus status, int remaining) {
        return new WorkBoardResponse.Topic(id, name, null, 0, due, null, status, false, remaining, 0, remaining,
                null, null);
    }

    @Test
    void 마감은_날짜순_지난것_포함_범위밖_제외() {
        List<Task> due = List.of(
                task("로그인 API", work, null, TODAY.minusDays(1)),           // 지남 → 포함
                task("아주 오래 지난 일", work, null, TODAY.minusDays(30)),    // 7일보다 오래 → 제외
                task("기출 1회", sqld, null, TODAY.plusDays(3)),
                task("너무 먼 일", sqld, null, TODAY.plusDays(20)),            // Task 는 14일까지 → 제외
                task("끝난 목표의 일", done, null, TODAY.plusDays(1)));        // 끝난 목표 → 제외
        List<TopicDeadline> topics = List.of(
                new TopicDeadline(work, topic(11, "이력서 제출", TODAY.plusDays(3), TopicStatus.NEXT, 2)),
                new TopicDeadline(work, topic(12, "기획", TODAY.minusDays(2), TopicStatus.DONE, 0)),   // 끝남 → 제외
                new TopicDeadline(work, topic(13, "포폴", null, TopicStatus.NO_DUE, 1)));             // 마감 없음 → 제외

        // 종료일이 두 달 뒤인 목표형도 나와요 (예전 30일 제한)
        GoalCategory toeic = GoalCategory.create(null, null, "토익 900", TODAY.minusDays(5), TODAY.plusDays(60), null);
        ReflectionTestUtils.setField(toeic, "goalCategoryId", 9L);
        ReflectionTestUtils.setField(toeic, "goalCategoryStatus", GoalCategoryStatus.IN_PROGRESS);

        List<Deadline> list = HomeCalculator.deadlines(TODAY, List.of(work, sqld, done, toeic), due, topics);

        assertThat(list).extracting(Deadline::title)
                .containsExactly("로그인 API", "기출 1회", "이력서 제출", "SQLD 합격", "토익 900");
        assertThat(list.get(0).overdue()).isTrue();
        assertThat(list.get(0).dDay()).isEqualTo(-1);
        // 같은 날이면 TASK 가 TOPIC 보다 먼저
        assertThat(list.get(2).type()).isEqualTo(DeadlineType.TOPIC);
        assertThat(list.get(2).remaining()).isEqualTo(2);
        assertThat(list.get(3).type()).isEqualTo(DeadlineType.GOAL);
        assertThat(list.get(3).dDay()).isEqualTo(20);
    }

    @Test
    void 목표별_오늘_끝낸일_못한일_회고() {
        Task a = completedOn(at(task("API 명세", work, null, null), TODAY, 9), TODAY);
        Task b = at(task("QA", work, null, null), TODAY, 14);                         // 오늘 잡혔는데 못 함
        Task c = completedOn(at(task("어제 잡은 일", work, null, null), TODAY.minusDays(1), 10), TODAY); // 오늘 끝냄
        Task d = task("기출 풀기", sqld, TODAY, null);                                // 시간 미정 · 오늘
        Retrospective r = Retrospective.builder().goalCategory(sqld).reviewType(ReviewType.DAILY)
                .reviewDate(TODAY).content("오답 정리가 밀렸다").build();
        ReflectionTestUtils.setField(r, "retrospectiveId", 7L);
        GoalCategory idle = GoalCategory.create(null, null, "쉬는 목표", TODAY.minusDays(3), null, null);
        ReflectionTestUtils.setField(idle, "goalCategoryId", 4L);
        ReflectionTestUtils.setField(idle, "goalCategoryStatus", GoalCategoryStatus.IN_PROGRESS);

        List<GoalToday> goals = HomeCalculator.goalsToday(TODAY, List.of(idle, sqld, work, done),
                List.of(a, b, c, d), List.of(r));

        // 끝난 목표는 빠지고, 오늘 한 게 있는 목표가 먼저 (끝낸 게 많은 순)
        assertThat(goals).extracting(GoalToday::goalName).containsExactly("나를 앱 v2", "SQLD 합격", "쉬는 목표");
        GoalToday w = goals.get(0);
        assertThat(w.planned()).isEqualTo(2);
        assertThat(w.done()).isEqualTo(2);
        assertThat(w.doneTasks()).containsExactly("API 명세", "어제 잡은 일");
        assertThat(w.openTasks()).containsExactly("QA");
        GoalToday s = goals.get(1);
        assertThat(s.planned()).isEqualTo(1);
        assertThat(s.retros()).extracting(HomeTodayResponse.Retro::content).containsExactly("오답 정리가 밀렸다");
        assertThat(goals.get(2).activeToday()).isFalse();
    }

    @Test
    void 목표형은_오늘기록_업무형은_회고만() {
        ReflectionTestUtils.setField(sqld, "startValue", new BigDecimal("40"));
        ReflectionTestUtils.setField(sqld, "targetValue", new BigDecimal("70"));
        ReflectionTestUtils.setField(sqld, "currentValue", new BigDecimal("52"));
        ReflectionTestUtils.setField(sqld, "metricName", "모의고사 점수");
        ReflectionTestUtils.setField(sqld, "metricUnit", "점");
        GoalCategory idle = GoalCategory.create(null, null, "독서", TODAY.minusDays(3), null, null);
        ReflectionTestUtils.setField(idle, "goalCategoryId", 4L);
        ReflectionTestUtils.setField(idle, "goalCategoryStatus", GoalCategoryStatus.IN_PROGRESS);

        Map<Long, HomeCalculator.DayRecord> records = Map.of(
                2L, new HomeCalculator.DayRecord(new BigDecimal("55"), ReflectionMood.GOOD, "기출 1회"),
                4L, new HomeCalculator.DayRecord(null, null, "30쪽 읽음"),
                1L, new HomeCalculator.DayRecord(new BigDecimal("1"), ReflectionMood.BAD, "무시돼요"));
        List<GoalToday> goals = HomeCalculator.goalsToday(TODAY, List.of(idle, sqld, work), List.of(), List.of(), records);

        GoalToday s = goals.stream().filter(g -> g.goalCategoryId() == 2L).findFirst().orElseThrow();
        assertThat(s.activeToday()).isTrue();
        assertThat(s.checkIn().hasMetric()).isTrue();
        assertThat(s.checkIn().metricName()).isEqualTo("모의고사 점수");
        assertThat(s.checkIn().value()).isEqualByComparingTo("55");
        assertThat(s.checkIn().mood()).isEqualTo("GOOD");

        // 수치가 없는 목표형: 회고만, 기록이 있으면 오늘 한 목표로
        GoalToday i = goals.stream().filter(g -> g.goalCategoryId() == 4L).findFirst().orElseThrow();
        assertThat(i.activeToday()).isTrue();
        assertThat(i.checkIn().hasMetric()).isFalse();
        assertThat(i.checkIn().note()).isEqualTo("30쪽 읽음");

        // 업무형은 지금처럼 회고 글만
        GoalToday w = goals.stream().filter(g -> g.goalCategoryId() == 1L).findFirst().orElseThrow();
        assertThat(w.checkIn()).isNull();
        assertThat(w.activeToday()).isFalse();
    }
}
