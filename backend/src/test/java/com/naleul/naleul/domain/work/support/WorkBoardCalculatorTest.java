package com.naleul.naleul.domain.work.support;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.work.dto.response.WorkBoardResponse;
import com.naleul.naleul.domain.work.dto.response.WorkTaskItem;
import com.naleul.naleul.domain.work.entity.WorkTopic;
import com.naleul.naleul.domain.work.enums.WorkBoardColumn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkBoardCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);
    private long seq = 1;
    private GoalCategory goal;
    private WorkTopic backend;
    private WorkTopic design;

    @BeforeEach
    void setUp() {
        goal = GoalCategory.createRecord(null, null, "나를 앱 v2", "💼", "백엔드 담당", TODAY.minusDays(10));
        ReflectionTestUtils.setField(goal, "goalCategoryId", 1L);
        backend = topic("백엔드", 0, TODAY.plusDays(10));
        design = topic("디자인 QA", 1, TODAY.plusDays(3));
    }

    private WorkTopic topic(String name, int order, LocalDate due) {
        WorkTopic t = WorkTopic.create(goal, name, null, due, order);
        ReflectionTestUtils.setField(t, "workTopicId", seq++);
        return t;
    }

    private Task task(String name, WorkTopic topic, LocalDate date, LocalDate due) {
        Task t = Task.createWorkTask(null, goal, null, topic, name, date, due, null);
        ReflectionTestUtils.setField(t, "taskId", seq++);
        return t;
    }

    private Task doneOn(Task t, LocalDate day) {
        t.complete();
        ReflectionTestUtils.setField(t, "completedAt", day.atTime(15, 0));
        return t;
    }

    private WorkBoardResponse build(List<Task> tasks) {
        return WorkBoardCalculator.build(goal, List.of(backend, design), tasks, List.of(), TODAY, 30);
    }

    @Test
    void 요약과_다음_마감_주제() {
        List<Task> tasks = new ArrayList<>();
        tasks.add(doneOn(task("API 명세", backend, TODAY.minusDays(2), null), TODAY.minusDays(2)));
        tasks.add(task("로그인 API", backend, null, TODAY.minusDays(1)));       // 마감 지남
        Task inProgress = task("QA 시트", design, TODAY, TODAY.plusDays(3));
        inProgress.markWorkStatus(WorkBoardColumn.IN_PROGRESS);
        tasks.add(inProgress);
        tasks.add(task("스토어 스크린샷", null, null, null));                   // 할 일 목록 · 주제 없음

        WorkBoardResponse.Summary s = build(tasks).summary();

        assertThat(s.total()).isEqualTo(4);
        assertThat(s.done()).isEqualTo(1);
        assertThat(s.inProgress()).isEqualTo(1);
        assertThat(s.notStarted()).isEqualTo(2);
        assertThat(s.remaining()).isEqualTo(3);
        assertThat(s.overdue()).isEqualTo(1);
        assertThat(s.dueThisWeek()).isEqualTo(1);
        assertThat(s.backlog()).isEqualTo(2);
        assertThat(s.progressPercent()).isEqualTo(25);
        // 다음 마감 = 마감이 더 가까운 디자인 QA (3일 뒤), 남은 1개 / 4일
        assertThat(s.nextTopic().name()).isEqualTo("디자인 QA");
        assertThat(s.nextTopic().dDay()).isEqualTo(3);
        assertThat(s.nextTopic().status()).isEqualTo(WorkBoardResponse.TopicStatus.NEXT);
        assertThat(s.nextTopic().remainingPerDay()).isEqualTo(0.3);
        assertThat(s.topicsTotal()).isEqualTo(2);
        assertThat(s.topicsDone()).isZero();
    }

    @Test
    void 로드맵은_마감_순서대로_끝난_주제는_DONE() {
        WorkTopic resume = topic("이력서 제출", 2, TODAY.minusDays(1));   // 마감 지남 · Task 없음
        WorkTopic study = topic("스터디", 3, null);                       // 마감 없음
        Task a = doneOn(task("API 명세", backend, null, null), TODAY);
        Task b = doneOn(task("ERD", backend, null, null), TODAY);          // 백엔드 Task 모두 끝남 → DONE

        WorkBoardResponse r = WorkBoardCalculator.build(goal, List.of(backend, design, resume, study), List.of(a, b),
                List.of(), TODAY, 30);

        assertThat(r.timeline()).extracting(WorkBoardResponse.Topic::name)
                .containsExactly("이력서 제출", "디자인 QA", "백엔드", "스터디");
        assertThat(r.timeline()).extracting(WorkBoardResponse.Topic::status).containsExactly(
                WorkBoardResponse.TopicStatus.OVERDUE, WorkBoardResponse.TopicStatus.UPCOMING,
                WorkBoardResponse.TopicStatus.DONE, WorkBoardResponse.TopicStatus.NO_DUE);
        // 다음 마감은 안 끝난 주제 중 가장 이른 마감 — 지난 것도 포함
        assertThat(r.summary().nextTopic().name()).isEqualTo("이력서 제출");
        assertThat(r.summary().topicsDone()).isEqualTo(1);

        // 직접 끝냈다고 표시하면 Task 가 없어도 DONE
        resume.markCompleted(true, TODAY.atTime(9, 0));
        WorkBoardResponse r2 = WorkBoardCalculator.build(goal, List.of(backend, design, resume, study), List.of(a, b),
                List.of(), TODAY, 30);
        assertThat(r2.timeline().get(0).status()).isEqualTo(WorkBoardResponse.TopicStatus.DONE);
        assertThat(r2.summary().nextTopic().name()).isEqualTo("디자인 QA");
        assertThat(r2.timeline().get(1).status()).isEqualTo(WorkBoardResponse.TopicStatus.NEXT);
    }

    @Test
    void 오늘_할_일은_마감_지난_것부터() {
        Task overdue = task("로그인 API", backend, null, TODAY.minusDays(1));
        Task todayDue = task("버그 수정", backend, null, TODAY);
        Task planned = task("회의록 정리", null, TODAY, null);
        Task past = task("어제 못 한 일", null, TODAY.minusDays(1), null);
        Task future = task("다음 주 일", null, TODAY.plusDays(5), TODAY.plusDays(7));

        List<WorkTaskItem> today = build(List.of(planned, future, todayDue, past, overdue)).todayTasks();

        assertThat(today).extracting(WorkTaskItem::taskName)
                .containsExactly("로그인 API", "버그 수정", "회의록 정리", "어제 못 한 일");
        assertThat(today.get(0).overdue()).isTrue();
        assertThat(today.get(0).dueInDays()).isEqualTo(-1);
    }

    @Test
    void 보드_주제별_아카이브() {
        Task a = doneOn(task("API 명세", backend, null, null), TODAY);
        Task b = doneOn(task("ERD", backend, null, null), TODAY.minusDays(1));
        Task c = task("로그인 API", backend, null, TODAY.plusDays(2));
        Task d = task("스토어 스크린샷", null, null, null);

        WorkBoardResponse r = build(List.of(a, b, c, d));

        assertThat(r.board().notStarted()).extracting(WorkTaskItem::taskName).containsExactly("로그인 API", "스토어 스크린샷");
        assertThat(r.board().done()).extracting(WorkTaskItem::taskName).containsExactly("API 명세", "ERD");
        assertThat(r.doneToday()).extracting(WorkTaskItem::taskName).containsExactly("API 명세");
        assertThat(r.upcoming()).extracting(WorkTaskItem::taskName).containsExactly("로그인 API");
        assertThat(r.archive()).extracting(WorkBoardResponse.ArchiveDay::date).containsExactly(TODAY, TODAY.minusDays(1));
        assertThat(r.topics()).extracting(WorkBoardResponse.Topic::name).containsExactly("백엔드", "디자인 QA", "세부 목표 없음");
        assertThat(r.topics().get(0).total()).isEqualTo(3);
        assertThat(r.topics().get(0).done()).isEqualTo(2);
        assertThat(r.backlog()).extracting(WorkTaskItem::taskName).containsExactly("로그인 API", "스토어 스크린샷");
    }

    @Test
    void 공유_텍스트() {
        Task inProgress = task("QA 시트", design, TODAY, null);
        inProgress.markWorkStatus(WorkBoardColumn.IN_PROGRESS);
        Task todo = task("로그인 API", backend, null, TODAY.plusDays(2));
        Task done = doneOn(task("API 명세", backend, null, null), TODAY);

        WorkBoardResponse r = build(List.of(inProgress, todo, done));
        String remaining = WorkBoardCalculator.shareText(r, "REMAINING");

        assertThat(remaining)
                .contains("📌 💼 나를 앱 v2")
                .contains("다음 마감: 디자인 QA 10/16(금) · D-3")
                .contains("[로드맵]\n▶ 디자인 QA (~10/16 · D-3 · 남은 1개)\n○ 백엔드 (~10/23 · 남은 1개)")
                .contains("진행 1/3 완료 (33%)")
                .contains("[진행 중]\n☐ QA 시트")
                .contains("[시작 전]\n· 백엔드\n☐ 로그인 API (~10/15)")
                .doesNotContain("API 명세");
        assertThat(WorkBoardCalculator.shareText(r, "ALL")).contains("[최근 완료 (7일)]\n☑ API 명세");
        assertThat(WorkBoardCalculator.shareText(r, "TODAY")).contains("[오늘 할 일 (10/13(화))]").contains("[오늘 끝낸 일]\n☑ API 명세");
    }

    @Test
    void 주제가_없으면_다음_마감도_없다() {
        WorkBoardResponse.Summary s = WorkBoardCalculator.build(goal, List.of(), List.of(), List.of(), TODAY, 30).summary();
        assertThat(s.nextTopic()).isNull();
        assertThat(s.progressPercent()).isNull();
    }
}
