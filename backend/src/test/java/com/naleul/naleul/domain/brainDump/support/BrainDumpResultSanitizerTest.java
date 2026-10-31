package com.naleul.naleul.domain.brainDump.support;

import com.naleul.naleul.domain.brainDump.client.BrainDumpAiRequest;
import com.naleul.naleul.domain.brainDump.client.BrainDumpAiResult;
import com.naleul.naleul.domain.brainDump.dto.request.BrainDumpParseRequest;
import com.naleul.naleul.domain.brainDump.dto.response.BrainDumpParseResponse;
import com.naleul.naleul.domain.brainDump.enums.DateType;
import com.naleul.naleul.domain.brainDump.enums.GoalMatchType;
import com.naleul.naleul.domain.task.enums.TaskKind;
import com.naleul.naleul.domain.task.enums.TaskPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BrainDumpResultSanitizerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    private static final List<BrainDumpAiRequest.GoalContext> GOALS = List.of(
            new BrainDumpAiRequest.GoalContext(10L, "토익 900점", "📘", false,
                    List.of(new BrainDumpAiRequest.SubGoal(100L, "LC 연습")),
                    List.of(new BrainDumpAiRequest.MilestoneContext(1000L, "LC 850점", TODAY.plusDays(7)))));

    private static BrainDumpParseRequest req(String... texts) {
        BrainDumpParseRequest.Item[] items = new BrainDumpParseRequest.Item[texts.length];
        for (int i = 0; i < texts.length; i++) items[i] = new BrainDumpParseRequest.Item("k" + i, texts[i], null, null, null, null);
        return new BrainDumpParseRequest(List.of(items));
    }

    private static BrainDumpAiResult.Item aiItem(String key, DateType type, LocalDate date, BrainDumpAiResult.Goal goal) {
        return new BrainDumpAiResult.Item(key, "제목", type, date, null, null, 60, TaskPriority.B, TaskKind.NORMAL, goal);
    }

    @Test
    @DisplayName("AI가 후보에 없는 목표·세부 목표·마일스톤 id를 주면 연결을 해제/무시한다")
    void rejectsUnknownIds() {
        BrainDumpAiResult ai = new BrainDumpAiResult(List.of(
                aiItem("k0", DateType.ON, TODAY, new BrainDumpAiResult.Goal(GoalMatchType.EXISTING, 999L, null, null, null, "x")),
                aiItem("k1", DateType.ON, TODAY, new BrainDumpAiResult.Goal(GoalMatchType.EXISTING, 10L, 555L, 1000L, null, "토익"))),
                List.of(), null);
        BrainDumpParseResponse r = BrainDumpResultSanitizer.sanitize(req("a", "b"), ai, GOALS, Map.of(10L, "#F80"), TODAY);

        assertThat(r.items().get(0).goal().type()).isEqualTo(GoalMatchType.NONE);
        BrainDumpParseResponse.GoalLink g = r.items().get(1).goal();
        assertThat(g.goalCategoryId()).isEqualTo(10L);
        assertThat(g.generalCategoryId()).isNull();        // 555 는 이 목표의 세부 목표가 아님
        assertThat(g.milestoneId()).isEqualTo(1000L);
        assertThat(g.goalColorCode()).isEqualTo("#F80");
        assertThat(r.aiFailed()).isFalse();
    }

    @Test
    @DisplayName("지난 날짜는 오늘로, DUE는 dueDate에, ON은 scheduledDate에")
    void dates() {
        BrainDumpAiResult ai = new BrainDumpAiResult(List.of(
                aiItem("k0", DateType.ON, TODAY.minusDays(3), null),
                aiItem("k1", DateType.DUE, TODAY.plusDays(2), null)), List.of(), null);
        BrainDumpParseResponse r = BrainDumpResultSanitizer.sanitize(req("a", "b"), ai, GOALS, Map.of(), TODAY);
        assertThat(r.items().get(0).scheduledDate()).isEqualTo(TODAY);
        assertThat(r.items().get(0).dueDate()).isNull();
        assertThat(r.items().get(1).dueDate()).isEqualTo(TODAY.plusDays(2));
        assertThat(r.items().get(1).scheduledDate()).isNull();
    }

    @Test
    @DisplayName("사용자가 칩으로 고른 날짜·시간이 AI보다 우선하고, 시각이 있으면 소요 시간은 시각 차이")
    void chipsWin() {
        BrainDumpParseRequest request = new BrainDumpParseRequest(List.of(new BrainDumpParseRequest.Item(
                "k0", "팀 회의", TODAY.plusDays(1), DateType.ON, LocalTime.of(14, 0), LocalTime.of(15, 30))));
        BrainDumpAiResult ai = new BrainDumpAiResult(List.of(aiItem("k0", DateType.DUE, TODAY.plusDays(5), null)), List.of(), null);
        BrainDumpParseResponse.Item i = BrainDumpResultSanitizer.sanitize(request, ai, GOALS, Map.of(), TODAY).items().get(0);
        assertThat(i.dateType()).isEqualTo(DateType.ON);
        assertThat(i.scheduledDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(i.estimatedMinutes()).isEqualTo(90);
    }

    @Test
    @DisplayName("AI가 시각을 놓치면 규칙 해석으로 채운다 (저녁 8시 → 20:00)")
    void ruleFillsMissingTime() {
        BrainDumpAiResult ai = new BrainDumpAiResult(List.of(aiItem("k0", DateType.ON, TODAY, null)), List.of(), null);
        BrainDumpParseResponse.Item i = BrainDumpResultSanitizer.sanitize(req("저녁 8시 정기 회의"), ai, GOALS, Map.of(), TODAY)
                .items().get(0);
        assertThat(i.startTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(i.dateType()).isEqualTo(DateType.ON);
        assertThat(i.scheduledDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("AI가 준 시각은 그대로 쓴다 (규칙으로 덮어쓰지 않음)")
    void aiTimeKept() {
        BrainDumpAiResult ai = new BrainDumpAiResult(List.of(new BrainDumpAiResult.Item("k0", "회의", DateType.ON, TODAY,
                LocalTime.of(19, 0), LocalTime.of(20, 0), 60, TaskPriority.B, TaskKind.APPOINTMENT, null)), List.of(), null);
        BrainDumpParseResponse.Item i = BrainDumpResultSanitizer.sanitize(req("저녁 8시 정기 회의"), ai, GOALS, Map.of(), TODAY)
                .items().get(0);
        assertThat(i.startTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(i.endTime()).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    @DisplayName("사용자가 정한 소요 시간이 AI 추정보다 우선하고, 종료 시각을 안 골랐으면 소요 시간으로 끝을 정한다")
    void userMinutesWin() {
        BrainDumpParseRequest request = new BrainDumpParseRequest(List.of(
                new BrainDumpParseRequest.Item("k0", "인강 듣기", null, null, null, null, 180),
                new BrainDumpParseRequest.Item("k1", "회의", null, null, LocalTime.of(20, 0), null, 120)));
        BrainDumpAiResult ai = new BrainDumpAiResult(List.of(
                aiItem("k0", DateType.ON, TODAY, null),
                new BrainDumpAiResult.Item("k1", "회의", DateType.ON, TODAY, LocalTime.of(20, 0), LocalTime.of(21, 0), 60,
                        TaskPriority.B, TaskKind.APPOINTMENT, null)), List.of(), null);
        List<BrainDumpParseResponse.Item> items = BrainDumpResultSanitizer.sanitize(request, ai, GOALS, Map.of(), TODAY).items();

        assertThat(items.get(0).estimatedMinutes()).isEqualTo(180);
        assertThat(items.get(0).startTime()).isNull();          // 시작 시각은 자동 배치가 정함
        assertThat(items.get(1).startTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(items.get(1).endTime()).isNull();            // AI 의 21:00 대신 소요 시간 2시간
        assertThat(items.get(1).estimatedMinutes()).isEqualTo(120);
    }

    @Test
    @DisplayName("AI가 실패하면(null) 규칙 해석 + 목표 미연결 + aiFailed")
    void fallback() {
        BrainDumpParseResponse r = BrainDumpResultSanitizer.sanitize(req("토익 LC 모의고사 내일까지"), null, GOALS, Map.of(), TODAY);
        assertThat(r.aiFailed()).isTrue();
        assertThat(r.items().get(0).dateType()).isEqualTo(DateType.DUE);
        assertThat(r.items().get(0).dueDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(r.items().get(0).goal().type()).isEqualTo(GoalMatchType.NONE);
        assertThat(r.items().get(0).estimatedMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("임시 목표는 실제로 쓰인 것만, 최대 3개 (넘치는 연결은 해제)")
    void tempGoals() {
        List<BrainDumpAiResult.Item> items = List.of(
                aiItem("k0", DateType.ON, TODAY, new BrainDumpAiResult.Goal(GoalMatchType.TEMP, null, null, null, "t1", "")),
                aiItem("k1", DateType.ON, TODAY, new BrainDumpAiResult.Goal(GoalMatchType.TEMP, null, null, null, "t2", "")),
                aiItem("k2", DateType.ON, TODAY, new BrainDumpAiResult.Goal(GoalMatchType.TEMP, null, null, null, "t3", "")),
                aiItem("k3", DateType.ON, TODAY, new BrainDumpAiResult.Goal(GoalMatchType.TEMP, null, null, null, "t4", "")));
        List<BrainDumpAiResult.TempGoal> temps = List.of(
                new BrainDumpAiResult.TempGoal("t1", "이사 준비", "🏠"), new BrainDumpAiResult.TempGoal("t2", "운동", "💪"),
                new BrainDumpAiResult.TempGoal("t3", "독서", "📚"), new BrainDumpAiResult.TempGoal("t4", "여행", "✈️"),
                new BrainDumpAiResult.TempGoal("t9", "안 쓰임", "❓"));
        BrainDumpParseResponse r = BrainDumpResultSanitizer.sanitize(req("a", "b", "c", "d"),
                new BrainDumpAiResult(items, temps, null), GOALS, Map.of(), TODAY);
        assertThat(r.tempGoals()).extracting(BrainDumpParseResponse.TempGoal::tempKey).containsExactly("t1", "t2", "t3");
        assertThat(r.items().get(3).goal().type()).isEqualTo(GoalMatchType.NONE);
    }

    @Test
    @DisplayName("길이를 정하지 않은 일은 AI 추정이 짧아도 최소 30분, 글로 말한 시간(20분)은 그대로")
    void unspecifiedMinutesAtLeast30() {
        BrainDumpAiResult ai = new BrainDumpAiResult(List.of(
                new BrainDumpAiResult.Item("k0", "병원 예약 전화", DateType.ON, TODAY, null, null, 15,
                        TaskPriority.C, TaskKind.NORMAL, null),
                new BrainDumpAiResult.Item("k1", "스트레칭", DateType.ON, TODAY, null, null, 30,
                        TaskPriority.C, TaskKind.NORMAL, null)), List.of(), null);
        List<BrainDumpParseResponse.Item> items = BrainDumpResultSanitizer.sanitize(
                req("병원 예약 전화", "스트레칭 20분"), ai, GOALS, Map.of(), TODAY).items();
        assertThat(items.get(0).estimatedMinutes()).isEqualTo(30);
        assertThat(items.get(1).estimatedMinutes()).isEqualTo(20);
    }
}
