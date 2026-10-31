package com.naleul.naleul.domain.activity.service;

import com.naleul.naleul.domain.activity.dto.request.ActivityLogRequest;
import com.naleul.naleul.global.exception.CustomException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 한 줄 기록 해석 · 시간 정하기 (DB 없이 순수 로직) */
class ActivityLogServiceTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 7);   // 수요일
    static final LocalDateTime NOW = TODAY.atTime(21, 10);

    private static ActivityLogRequest.Item item(String text) {
        return new ActivityLogRequest.Item("c1", text, null, null, null, null, null, null, null);
    }

    @Test
    void 걸린_시간은_제목에서_빼고_분으로_읽는다() {
        ActivityLogService.Planned p = ActivityLogService.plan(item("운동 30분"), TODAY);

        assertThat(p.title()).isEqualTo("운동");
        assertThat(p.minutes()).isEqualTo(30);
        assertThat(p.start()).isNull();
        assertThat(p.date()).isEqualTo(TODAY);
    }

    @Test
    void 시각과_걸린_시간을_함께_읽는다() {
        ActivityLogService.Planned p = ActivityLogService.plan(item("저녁 8시 나를 회의 1시간"), TODAY);

        assertThat(p.title()).isEqualTo("나를 회의");
        assertThat(p.start()).isEqualTo(LocalTime.of(20, 0));
        assertThat(p.minutes()).isEqualTo(60);

        ActivityLogService.Range r = ActivityLogService.explicitRange(p, NOW);
        assertThat(r.start()).isEqualTo(TODAY.atTime(20, 0));
        assertThat(r.end()).isEqualTo(TODAY.atTime(21, 0));
    }

    @Test
    void 어제는_지난_날짜로() {
        ActivityLogService.Planned p = ActivityLogService.plan(item("어제 기획서 작성 2시간"), TODAY);

        assertThat(p.date()).isEqualTo(TODAY.minusDays(1));
        assertThat(p.title()).isEqualTo("기획서 작성");
    }

    @Test
    void 요일만_쓰면_지난_그_요일로() {
        // 수요일에 "월요일" → 다가오는 월요일이 아니라 이번 주 월요일
        assertThat(ActivityLogService.pastOrToday(LocalDate.of(2026, 10, 12), TODAY)).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(ActivityLogService.pastOrToday(TODAY.plusDays(30), TODAY)).isEqualTo(TODAY);
        assertThat(ActivityLogService.pastOrToday(null, TODAY)).isEqualTo(TODAY);
    }

    @Test
    void 하는_중인_일은_지금까지로_자르고_시작부터_미래면_오류() {
        ActivityLogService.Planned ongoing = new ActivityLogService.Planned("c1", "회의", TODAY, LocalTime.of(20, 30), null, 120);
        assertThat(ActivityLogService.explicitRange(ongoing, NOW).end()).isEqualTo(NOW);

        ActivityLogService.Planned future = new ActivityLogService.Planned("c2", "회의", TODAY, LocalTime.of(22, 0), null, 60);
        assertThatThrownBy(() -> ActivityLogService.explicitRange(future, NOW)).isInstanceOf(CustomException.class);
    }

    @Test
    void AI_해석_결과를_보내면_그_값이_우선() {
        ActivityLogRequest.Item fromAi = new ActivityLogRequest.Item("c1", "운동 30분", "헬스", null, null, null, 45, 3L, null);

        ActivityLogService.Planned p = ActivityLogService.plan(fromAi, TODAY);

        assertThat(p.title()).isEqualTo("헬스");
        assertThat(p.minutes()).isEqualTo(45);
    }
}
