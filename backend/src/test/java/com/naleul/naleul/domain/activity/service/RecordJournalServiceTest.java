package com.naleul.naleul.domain.activity.service;

import com.naleul.naleul.domain.activity.dto.response.RecordJournalResponse;
import com.naleul.naleul.domain.activity.dto.response.RecordJournalResponse.Item;
import com.naleul.naleul.domain.activity.service.RecordJournalService.Dated;
import com.naleul.naleul.domain.activity.service.RecordJournalService.TaskItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 업무 일지 묶기 (DB 없이 순수 로직) */
class RecordJournalServiceTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

    private static TaskItem task(long id, String name, LocalDate date, Integer hour, Long minutes) {
        LocalDateTime start = hour != null ? date.atTime(hour, 0) : null;
        return new TaskItem(date, new Item("TASK", id, name, null, start,
                start != null && minutes != null ? start.plusMinutes(minutes) : null, minutes, false, null));
    }

    private static Dated record(long id, String title, LocalDate date, int hour, long minutes) {
        LocalDateTime start = date.atTime(hour, 0);
        return new Dated(date, new Item("RECORD", id, title, null, start, start.plusMinutes(minutes), minutes, false, null));
    }

    @Test
    void 날짜별로_최근_날짜부터_묶고_하루_안에서는_시각순_시각_없는_Task는_뒤로() {
        RecordJournalResponse r = RecordJournalService.build(
                List.of(task(1, "기획서", TODAY, 14, 60L), task(2, "메일 정리", TODAY, null, null),
                        task(3, "주간 회의", TODAY.minusDays(1), 10, 60L)),
                List.of(record(10, "코드 리뷰", TODAY, 9, 30)),
                TODAY.minusDays(13), TODAY.plusDays(1), false);

        assertThat(r.days()).extracting(RecordJournalResponse.Day::date).containsExactly(TODAY, TODAY.minusDays(1));
        RecordJournalResponse.Day today = r.days().get(0);
        assertThat(today.items()).extracting(Item::title).containsExactly("코드 리뷰", "기획서", "메일 정리");
        assertThat(today.taskCount()).isEqualTo(2);
        assertThat(today.recordCount()).isEqualTo(1);
        assertThat(today.minutes()).isEqualTo(90);
        assertThat(r.nextBefore()).isNull();
    }

    @Test
    void 범위_밖_항목은_빼고_더_오래된_일지가_있으면_다음_쪽_날짜를_준다() {
        LocalDate from = TODAY.minusDays(13);
        RecordJournalResponse r = RecordJournalService.build(
                List.of(task(1, "오래된 일", from.minusDays(1), 9, 30L), task(2, "오늘 일", TODAY, 9, 30L)),
                List.of(), from, TODAY.plusDays(1), true);

        assertThat(r.days()).hasSize(1);
        assertThat(r.nextBefore()).isEqualTo(from);
    }
}
