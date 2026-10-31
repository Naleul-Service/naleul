package com.naleul.naleul.domain.externalCalendar.service;

import com.naleul.naleul.domain.externalCalendar.dto.request.ExternalEventImportRequest.Event;
import com.naleul.naleul.domain.externalCalendar.service.ExternalEventImportPlanner.Plan;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 외부 일정 → 만들 Task 목록 */
class ExternalEventImportPlannerTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 9);
    static final LocalDateTime AT_10 = TODAY.plusDays(1).atTime(10, 0);

    private static Event timed(String id, String title, LocalDateTime start, LocalDateTime end) {
        return new Event(id, title, "회사", false, start, end, null);
    }

    private static Event allDay(String id, String title, LocalDate date) {
        return new Event(id, title, null, true, null, null, date);
    }

    @Test
    void 시간_일정과_하루_종일_일정을_만든다() {
        Plan p = ExternalEventImportPlanner.plan(List.of(
                timed("g1", "  주간 회의 ", AT_10, AT_10.plusMinutes(50)),
                allDay("g2", "엄마 생신", TODAY.plusDays(3))), TODAY, List.of(), List.of());
        assertThat(p.drafts().size()).isEqualTo(2);
        assertThat(p.drafts().get(0).title()).isEqualTo("주간 회의");
        assertThat(p.drafts().get(0).minutes()).isEqualTo(50L);
        assertThat(p.drafts().get(1).allDay()).isTrue();
        assertThat(p.drafts().get(1).date()).isEqualTo(TODAY.plusDays(3));
    }

    @Test
    void 이미_가져온_일정과_같은_이름_같은_시각_Task는_건너뛴다() {
        Plan p = ExternalEventImportPlanner.plan(List.of(
                        timed("g1", "주간 회의", AT_10, AT_10.plusHours(1)),          // 이미 가져온 id
                        timed("a9", "치과", AT_10.plusHours(3), AT_10.plusHours(4)),  // 같은 이름 · 시각 Task 있음
                        timed("a10", "점심", AT_10.plusHours(2), AT_10.plusHours(3)),
                        timed("a10", "점심", AT_10.plusHours(2), AT_10.plusHours(3))), // 같은 요청 안 중복
                TODAY, List.of("g1"), List.of(ExternalEventImportPlanner.nameStartKey("치과", AT_10.plusHours(3))));
        assertThat(p.drafts().size()).isEqualTo(1);
        assertThat(p.skippedDuplicate()).isEqualTo(3);
    }

    @Test
    void 지난_일정은_건너뛰고_오늘_일정은_가져온다() {
        Plan p = ExternalEventImportPlanner.plan(List.of(
                timed("p1", "어제 회의", AT_10.minusDays(2), AT_10.minusDays(2).plusHours(1)),
                timed("t1", "오늘 아침 회의", TODAY.atTime(8, 0), TODAY.atTime(9, 0))), TODAY, List.of(), List.of());
        assertThat(p.skippedPast()).isEqualTo(1);
        assertThat(p.drafts().size()).isEqualTo(1);
    }

    @Test
    void 시간이_틀리거나_비었으면_건너뛰고_하루_넘는_일정은_하루_종일로() {
        Plan p = ExternalEventImportPlanner.plan(List.of(
                timed("x1", "거꾸로", AT_10, AT_10.minusHours(1)),
                timed("x2", "", AT_10, AT_10.plusHours(1)),
                allDay("x3", "날짜 없음", null),
                timed("x4", "1년 뒤", AT_10.plusDays(400), AT_10.plusDays(400).plusHours(1)),
                timed("x5", "제주 여행", AT_10, AT_10.plusDays(3))), TODAY, List.of(), List.of());
        assertThat(p.skippedInvalid()).isEqualTo(4);
        assertThat(p.drafts().size()).isEqualTo(1);
        assertThat(p.drafts().get(0).allDay()).isTrue();
        assertThat(p.drafts().get(0).date()).isEqualTo(AT_10.toLocalDate());
    }

    @Test
    void 긴_제목은_100자로_자른다() {
        Plan p = ExternalEventImportPlanner.plan(List.of(timed("l1", "가".repeat(150), AT_10, AT_10.plusHours(1))),
                TODAY, List.of(), List.of());
        assertThat(p.drafts().get(0).title().length()).isEqualTo(100);
    }
}
